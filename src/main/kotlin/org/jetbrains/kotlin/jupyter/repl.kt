package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jupyter.magic.*
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
    constructor (message: String) : this(ReplCompileResult.Error(message, null))
}

class ReplForJupyter(val baseClasspath: List<File> = emptyList(), config: ResolverConfig? = null) {

    private val resolver = JupyterScriptDependenciesResolver(config)

    private val renderers = config?.let { it.libraries.flatMap { it.value.renderers } }?.map { it.className to it }?.toMap().orEmpty()

    private val useMagicHandler = LibrariesMagicHandler(config?.libraries.orEmpty())

    private val includedLibraries = mutableSetOf<LibraryDefinition>()

    val codePreprocessor = CompoundCodePreprocessor(
            DelegatedCodePreprocessor {
                val initCellCode = includedLibraries.flatMap { it.initCell }.joinToString(separator = "\n")
                if (initCellCode.isNotBlank()) initCellCode + "\n" + it else it
            },
            MagicProcessor(
                    useMagicHandler,
                    EnableOptionMagicHandler("trackClasspath") { trackClasspath = true },
                    EnableOptionMagicHandler("trackCode") { trackExecutedCode = true }
            )
    )

    private val receiver = KotlinReceiver()

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

    private val compilerConfiguration by lazy {
        ScriptCompilationConfiguration {
            hostConfiguration.update { it.withDefaultsFrom(defaultJvmScriptingHostConfiguration) }
            baseClass.put(KotlinType(ScriptTemplateWithDisplayHelpers::class))
            fileExtension.put("jupyter.kts")
            defaultImports(DependsOn::class, Repository::class, ScriptTemplateWithDisplayHelpers::class)
            jvm {
                updateClasspath(baseClasspath)
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

    val ScriptCompilationConfiguration.classpath
        get() = this[ScriptCompilationConfiguration.dependencies]
                ?.filterIsInstance<JvmDependency>()
                ?.flatMap { it.classpath }
                .orEmpty()

    val currentClasspath = mutableSetOf<String>().also { it.addAll(compilerConfiguration.classpath.map { it.canonicalPath }) }

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

    private var trackClasspath: Boolean = false

    private var trackExecutedCode: Boolean = false

    fun checkComplete(executionNumber: Long, code: String): CheckResult {
        val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
        return when (val result = compiler.check(compilerState, codeLine)) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(LineId(codeLine), true)
            is ReplCheckResult.Incomplete -> CheckResult(LineId(codeLine), false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    init {
        // TODO: to be removed after investigation of https://github.com/erokhins/kotlin-jupyter/issues/24
        eval("1")
    }

    fun eval(code: String): EvalResult {
        try {
            val processedCode = codePreprocessor.process(code)

            var result = doEval(processedCode)

            // on successful execution add all libraries, that were added via '%use' magic, to the set
            includedLibraries.addAll(useMagicHandler.popAddedLibraries())

            val number = executionCounter - 1
            val displays = mutableListOf<Any>()

            if (trackExecutedCode)
                displays.add("Executed code:\n$processedCode")

            val resolvedClasspath = resolver.popAddedClasspath().map { it.canonicalPath }
            if (resolvedClasspath.isNotEmpty()) {

                val newClasspath = resolvedClasspath.filter { !currentClasspath.contains(it) }
                val oldClasspath = resolvedClasspath.filter { currentClasspath.contains(it) }
                currentClasspath.addAll(newClasspath)
                if (trackClasspath) {
                    val sb = StringBuilder()
                    if (newClasspath.count() > 0) {
                        sb.appendln("${newClasspath.count()} new paths were added to classpath:")
                        newClasspath.sortedBy { it }.forEach { sb.appendln(it) }
                    }
                    if (oldClasspath.count() > 0) {
                        sb.appendln("${oldClasspath.count()} resolved paths were already in classpath:")
                        oldClasspath.sortedBy { it }.forEach { sb.appendln(it) }
                    }
                    sb.appendln("Current classpath size: ${currentClasspath.count()}")
                    displays.add(sb.toString())
                }
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
        } finally {
            useMagicHandler.popAddedLibraries()
        }
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
        log.info("Classpath used in script: ${baseClasspath}")
    }
}

