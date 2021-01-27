package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlinx.jupyter.CheckResult
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplException
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.compiler.util.actualClassLoader
import org.jetbrains.kotlinx.jupyter.compiler.util.getErrors
import org.jetbrains.kotlinx.jupyter.config.readResourceAsIniFile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.analysisDiagnostics
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvm.util.isIncomplete
import kotlin.script.experimental.jvm.util.toSourceCodePosition
import kotlin.script.experimental.util.LinkedSnippet

interface JupyterCompiler {

    val version: KotlinKernelVersion
    val numberOfSnippets: Int
    val previousScriptsClasses: List<KClass<*>>
    val lastKClass: KClass<*>
    val lastClassLoader: ClassLoader

    fun nextCounter(): Int
    fun updateCompilationConfig(body: ScriptCompilationConfiguration.Builder.() -> Unit)
    fun compileSync(snippet: SourceCode): Result

    data class Result(
        val snippet: LinkedSnippet<KJvmCompiledScript>,
        val newEvaluationConfiguration: ScriptEvaluationConfiguration,
    )
}

interface JupyterCompilerWithCompletion : JupyterCompiler {

    val completer: ReplCompleter

    fun checkComplete(code: Code): CheckResult

    fun listErrors(code: Code): Sequence<ScriptDiagnostic>

    companion object {
        fun create(
            compilationConfiguration: ScriptCompilationConfiguration,
            evaluationConfiguration: ScriptEvaluationConfiguration,
            lightCompilationConfiguration: ScriptCompilationConfiguration
        ): JupyterCompilerWithCompletion {
            return JupyterCompilerWithCompletionImpl(
                KJvmReplCompilerWithIdeServices(
                    compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                        ?: defaultJvmScriptingHostConfiguration
                ),
                compilationConfiguration,
                evaluationConfiguration,
                lightCompilationConfiguration
            )
        }
    }
}

open class JupyterCompilerImpl<CompilerT : ReplCompiler<KJvmCompiledScript>>(
    protected val compiler: CompilerT,
    private var compilationConfig: ScriptCompilationConfiguration,
    private val basicEvaluationConfiguration: ScriptEvaluationConfiguration,
) : JupyterCompiler {
    private val executionCounter = AtomicInteger()
    private val classes = mutableListOf<KClass<*>>()
    private val properties = readResourceAsIniFile("compiler.properties", JupyterCompilerImpl::class.java.classLoader)

    override val version: KotlinKernelVersion =
        KotlinKernelVersion.from(
            properties["version"] ?: error("Compiler artifact should contain version")
        )!!

    override val numberOfSnippets: Int
        get() = classes.size

    override val previousScriptsClasses: List<KClass<*>>
        get() = classes

    override val lastKClass: KClass<*>
        get() = classes.last()

    override val lastClassLoader: ClassLoader
        get() = classes.lastOrNull()?.java?.classLoader
            ?: basicEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]!!

    override fun nextCounter() = executionCounter.getAndIncrement()

    override fun updateCompilationConfig(body: ScriptCompilationConfiguration.Builder.() -> Unit) {
        compilationConfig = compilationConfig.with(body)
    }

    override fun compileSync(snippet: SourceCode): JupyterCompiler.Result {
        when (val resultWithDiagnostics = runBlocking { compiler.compile(snippet, compilationConfig) }) {
            is ResultWithDiagnostics.Failure -> throw ReplCompilerException(resultWithDiagnostics)
            is ResultWithDiagnostics.Success -> {
                val result = resultWithDiagnostics.value
                val compiledScript = result.get()

                val configWithClassloader = basicEvaluationConfiguration.with {
                    val lastClassOrNull = classes.lastOrNull()
                    if (lastClassOrNull != null) {
                        jvm {
                            baseClassLoader(lastClassOrNull.java.classLoader)
                        }
                    }
                }
                val classLoader = compiledScript.getOrCreateActualClassloader(configWithClassloader)
                val newEvaluationConfiguration = configWithClassloader.with {
                    jvm {
                        actualClassLoader(classLoader)
                    }
                }

                when (val kClassWithDiagnostics = runBlocking { compiledScript.getClass(newEvaluationConfiguration) }) {
                    is ResultWithDiagnostics.Failure -> throw ReplCompilerException(kClassWithDiagnostics)
                    is ResultWithDiagnostics.Success -> {
                        val kClass = kClassWithDiagnostics.value
                        classes.add(kClass)
                        return JupyterCompiler.Result(result, newEvaluationConfiguration)
                    }
                }
            }
        }
        throw ReplCompilerException("Impossible situation: this code should be unreachable")
    }
}

class JupyterCompilerWithCompletionImpl(
    compiler: KJvmReplCompilerWithIdeServices,
    compilationConfig: ScriptCompilationConfiguration,
    evaluationConfig: ScriptEvaluationConfiguration,
    val configForAnalyze: ScriptCompilationConfiguration
) : JupyterCompilerImpl<KJvmReplCompilerWithIdeServices>(compiler, compilationConfig, evaluationConfig),
    JupyterCompilerWithCompletion {

    override val completer: ReplCompleter
        get() = compiler

    override fun checkComplete(
        code: Code
    ): CheckResult {
        val result = analyze(code)
        return when {
            result.isIncomplete() -> CheckResult(false)
            result.isError() -> throw ReplException(result.getErrors())
            else -> CheckResult(true)
        }
    }

    private fun analyze(
        code: Code
    ): ResultWithDiagnostics<ReplAnalyzerResult> {
        val snippet = SourceCodeImpl(nextCounter(), code)

        val result = runBlocking {
            compiler.analyze(
                snippet,
                0.toSourceCodePosition(snippet),
                configForAnalyze
            )
        }
        return result
    }

    override fun listErrors(code: Code): Sequence<ScriptDiagnostic> {
        val result = analyze(code).valueOrThrow()

        return result[ReplAnalyzerResult.analysisDiagnostics]!!
    }
}

fun getSimpleCompiler(
    compilationConfiguration: ScriptCompilationConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration,
): JupyterCompiler {
    class SimpleReplCompiler(hostConfiguration: ScriptingHostConfiguration) :
        KJvmReplCompilerBase<ReplCodeAnalyzerBase>(
            hostConfiguration = hostConfiguration,
            initAnalyzer = { sharedScriptCompilationContext, scopeProcessor ->
                ReplCodeAnalyzerBase(
                    sharedScriptCompilationContext.environment,
                    implicitsResolutionFilter = scopeProcessor
                )
            }
        )

    return JupyterCompilerImpl(
        SimpleReplCompiler(
            compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                ?: defaultJvmScriptingHostConfiguration
        ),
        compilationConfiguration,
        evaluationConfiguration
    )
}
