package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.completion.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.context.KotlinContext
import org.jetbrains.kotlin.jupyter.repl.context.KotlinReceiver
import org.jetbrains.kotlin.jupyter.repl.reflect.ContextUpdater
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

data class EvalResult(val resultValue: Any?, val displayValues: List<Any> = emptyList())

data class CheckResult(val codeLine: LineId, val isComplete: Boolean = true)

open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReplEvalRuntimeException(val errorResult: ReplEvalResult.Error.Runtime) : ReplException(errorResult.message, errorResult.cause)

class ReplCompilerException(val errorResult: ReplCompileResult.Error) : ReplException(errorResult.message) {
    constructor (checkResult: ReplCheckResult.Error) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplCompileResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (checkResult: ReplEvalResult.Error.CompileTime) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplEvalResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (historyMismatchResult: ReplEvalResult.HistoryMismatch) : this(ReplCompileResult.Error("History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
}

class ReplForJupyter(val classpath: List<File> = emptyList(), val config: ResolverConfig? = null) {

    private val resolver = JupyterScriptDependenciesResolver(config)

    private val renderers = config?.let { it.libraries.flatMap { it.value.renderers } }?.map { it.className to it }?.toMap().orEmpty()

    private val libraryMagics = config?.let { it.libraries.keys.map { "%%$it" to "@file:DependsOn(\"$it\")" } }?.toMap().orEmpty()

    private fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
                ?: return context.compilationConfiguration.asSuccess()
        val scriptContents = object : ScriptContents {
            override val annotations: Iterable<Annotation> = annotations
            override val file: File? = null
            override val text: CharSequence? = null
        }
        return try {
            resolver.resolveFromAnnotations(scriptContents)
                .onSuccess { classpath ->
                    context.compilationConfiguration
                            .let { if (classpath.isEmpty()) it else it.withUpdatedClasspath(classpath) }
                            .asSuccess()
                }
        } catch (e: Throwable) {
            ResultWithDiagnostics.Failure(e.asDiagnostics(path = context.script.locationId))
        }
    }

    private val receiver = KotlinReceiver()

    private val compilerConfiguration by lazy {
        ScriptCompilationConfiguration {
            hostConfiguration.update { it.withDefaultsFrom(defaultJvmScriptingHostConfiguration) }
            baseClass.put(KotlinType(ScriptTemplateWithDisplayHelpers::class))
            fileExtension.put("jupyter.kts")
            defaultImports(DependsOn::class, Repository::class, ScriptTemplateWithDisplayHelpers::class)
            jvm {
                updateClasspath(classpath)
            }
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, handler = { configureMavenDepsOnAnnotations(it) })
            }

            val kt = KotlinType(receiver.javaClass.canonicalName)
            implicitReceivers.invoke(listOf(kt))

            val classes = listOf(receiver.javaClass, ScriptTemplateWithDisplayHelpers::class.java)
            val classPath = classes.asSequence().map { it.protectionDomain.codeSource.location.path }.joinToString(":")
            compilerOptions.invoke(listOf("-classpath", classPath, "-jvm-target", "1.8"))
        }
    }

    private val evaluatorConfiguration = ScriptEvaluationConfiguration {
        implicitReceivers.invoke(receiver)
    }

    private var executionCounter = 0

    private val compiler: ReplCompiler by lazy {
        JvmReplCompiler(compilerConfiguration)
    }

    private val evaluator: ReplEvaluator by lazy {
        JvmReplEvaluator(evaluatorConfiguration)
    }

    private val stateLock = ReentrantReadWriteLock()

    private val compilerState = compiler.createState(stateLock)
    private val evaluatorState = evaluator.createState(stateLock)

    private val state = AggregatedReplStageState(compilerState, evaluatorState, stateLock)

    private val ctx = KotlinContext()
    private val contextUpdater = ContextUpdater(state, ctx.vars, ctx.functions)

    private val completer = KotlinCompleter(ctx)

    fun checkComplete(executionNumber: Long, code: String): CheckResult {
        val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
        return when(val result = compiler.check(compilerState, codeLine)) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(LineId(codeLine), true)
            is ReplCheckResult.Incomplete -> CheckResult(LineId(codeLine), false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    init {
        eval("1")
    }

    private fun replaceMagics(code: String) =
            libraryMagics.asSequence().fold(code) { str, magic ->
                str.replace(magic.key, magic.value)
            }

    fun eval(code: String): EvalResult {
        val processedCode = replaceMagics(code)
        var result = doEval(processedCode)
        val number = executionCounter - 1
        val extraCode = resolver.getAdditionalInitializationCode()
        val displays = mutableListOf<Any>()
        if (extraCode.isNotEmpty()) {
            displays.addAll(extraCode.mapNotNull(::doEval))
        }
        if (result != null) {
            renderers[result.javaClass.canonicalName]?.let {
                it.displayCode?.replace("\$it", "res$number")?.let(::doEval)?.let(displays::add)
                it.resultCode?.let {
                    result = if (it.trim().isBlank()) ""
                    else doEval(it.replace("\$it", "res$number"))
                }
            }
        }
        return EvalResult(result, displays)
    }


    fun complete(code: String, cursor: Int): CompletionResult = completer.complete(code, cursor)

    private fun doEval(code: String): Any? {
        synchronized(this) {
            val codeLine = ReplCodeLine(executionCounter++, 0, code)
            when (val compileResult = compiler.compile(compilerState, codeLine)) {
                is ReplCompileResult.CompiledClasses -> {
                    val result = evaluator.eval(evaluatorState, compileResult)
                    contextUpdater.update()
                    return when (result) {
                        is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(result)
                        is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(result)
                        is ReplEvalResult.Incomplete -> throw ReplCompilerException(result)
                        is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(result)
                        is ReplEvalResult.UnitResult -> {
                            Unit
                        }
                        is ReplEvalResult.ValueResult -> {
                            result.value
                        }
                        else -> throw IllegalStateException("Unknown eval result type ${this}")
                    }
                }
                is ReplCompileResult.Error -> throw ReplCompilerException(compileResult)
                is ReplCompileResult.Incomplete -> throw ReplCompilerException(compileResult)
            }
        }
    }

    init {
        log.info("Starting kotlin REPL engine. Compiler version: ${KotlinCompilerVersion.VERSION}")
        log.info("Classpath used in script: ${classpath}")
    }
}

