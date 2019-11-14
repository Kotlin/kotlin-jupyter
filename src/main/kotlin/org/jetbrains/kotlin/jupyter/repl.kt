package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

data class EvalResult(val codeLine: LineId, val resultValue: Any?)

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

class ReplForJupyter(val classpath: List<File> = emptyList(), libraries: LibrariesConfig? = null) {

    private val resolver = JupyterScriptDependenciesResolver(libraries)

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
            defaultImports(DependsOn::class, Repository::class)
            jvm {
                updateClasspath(classpath)
            }
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, handler = { configureMavenDepsOnAnnotations(it) })
            }
        }
    }

    private val evaluatorConfiguration = ScriptEvaluationConfiguration { }

    private var executionCounter = 0

    private val compiler: ReplCompiler by lazy {
        JvmReplCompiler(compilerConfiguration)
    }

    private val evaluator: ReplEvaluator by lazy {
        JvmReplEvaluator(evaluatorConfiguration)
    }

    private val stateLock = ReentrantReadWriteLock()

    private val state = compiler.createState(stateLock)

    private val evaluatorState = evaluator.createState(stateLock)

    fun checkComplete(executionNumber: Long, code: String): CheckResult {
        val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
        var result = compiler.check(state, codeLine)
        return when(result) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(LineId(codeLine), true)
            is ReplCheckResult.Incomplete -> CheckResult(LineId(codeLine), false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    fun eval(code: String): EvalResult {
        val result = doEval(code)
        val newImports = resolver.getNewImports()
        if (!newImports.isEmpty()) {
            val importsCode = newImports.joinToString("\n") { "import $it" }
            doEval(importsCode)
            return result
        }
        return result
    }

    private fun doEval(code: String): EvalResult {
            synchronized(this) {
                val codeLine = ReplCodeLine(executionCounter++, 0, code)
                val compileResult = compiler.compile(state, codeLine)
                when (compileResult) {
                    is ReplCompileResult.CompiledClasses -> {
                        var result = evaluator.eval(evaluatorState, compileResult)
                        return when (result) {
                            is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(result)
                            is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(result)
                            is ReplEvalResult.Incomplete -> throw ReplCompilerException(result)
                            is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(result)
                            is ReplEvalResult.UnitResult -> {
                                EvalResult(LineId(codeLine), Unit)
                            }
                            is ReplEvalResult.ValueResult -> {
                                EvalResult(LineId(codeLine), result.value)
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

