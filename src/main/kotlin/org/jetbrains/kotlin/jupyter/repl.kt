package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.MimeTypedResult
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.*
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
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

class ReplForJupyter(val additionalClasspath: List<File> = emptyList()) {

    private fun ReplEvalResult.toResult(codeLine: LineId): EvalResult {
        return when (this) {
            is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(this)
            is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(this)
            is ReplEvalResult.Incomplete -> throw ReplCompilerException(this)
            is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(this)
            is ReplEvalResult.UnitResult -> {
                EvalResult(codeLine, Unit)
            }
            is ReplEvalResult.ValueResult -> {
                EvalResult(codeLine, this.value)
            }
            else -> throw IllegalStateException("Unknown eval result type ${this}")
        }
    }


    private val compilerConfiguration by lazy {
        createJvmCompilationConfigurationFromTemplate<ScriptTemplateWithDisplayHelpers> {
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
                dependenciesFromClassContext(ScriptTemplateWithDisplayHelpers::class)
                dependenciesFromClassContext(MimeTypedResult::class)
                updateClasspath(additionalClasspath)
            }
        }
    }

    private val evaluatorConfiguration =
        ScriptEvaluationConfiguration {

        }

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

    //TODO fix classpath
    val classpath: String get() = System.getProperty("java.class.path")!!

    fun eval(executionNumber: Long, code: String): EvalResult {
        synchronized(this) {
            val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
            val compileResult = compiler.compile(state, codeLine)
            when(compileResult){
                is ReplCompileResult.CompiledClasses -> {
                    var result = evaluator.eval(evaluatorState, compileResult)
                    return result.toResult(LineId(codeLine))
                }
                is ReplCompileResult.Error -> throw ReplCompilerException(compileResult)
                is ReplCompileResult.Incomplete -> throw ReplCompilerException(compileResult)
            }
        }
    }

    init {
        log.info("Starting kotlin repl ${KotlinCompilerVersion.VERSION}")
        log.info("Using classpath:\n${classpath}")
    }
}

