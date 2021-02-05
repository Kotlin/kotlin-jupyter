package org.jetbrains.kotlin.jupyter.compiler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlin.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlin.jupyter.compiler.util.actualClassLoader
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.LinkedSnippet

class JupyterCompiler<CompilerT : ReplCompiler<KJvmCompiledScript>>(
    val compiler: CompilerT,
    private val basicCompilationConfiguration: ScriptCompilationConfiguration,
    private val basicEvaluationConfiguration: ScriptEvaluationConfiguration,
) : ReplCompiler<KJvmCompiledScript> by compiler {
    private val executionCounter = AtomicInteger()
    private val classes = mutableListOf<KClass<*>>()

    val numberOfSnippets: Int
        get() = generateSequence(lastCompiledSnippet) { it.previous }.count()

    val previousScriptsClasses: List<KClass<*>>
        get() = classes

    val lastKClass: KClass<*>
        get() = classes.last()

    fun nextCounter() = executionCounter.getAndIncrement()
    fun nextSourceCode(code: String): SourceCode = SourceCodeImpl(nextCounter(), code)

    fun compileSync(snippet: SourceCode): Result {
        when (val resultWithDiagnostics = runBlocking { compile(snippet, basicCompilationConfiguration) }) {
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
                        return Result(result, newEvaluationConfiguration)
                    }
                }
            }
        }
    }

    data class Result(
        val snippet: LinkedSnippet<KJvmCompiledScript>,
        val newEvaluationConfiguration: ScriptEvaluationConfiguration,
    )
}

class SimpleReplCompiler : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(
    initAnalyzer = { sharedScriptCompilationContext, scopeProcessor ->
        ReplCodeAnalyzerBase(sharedScriptCompilationContext.environment, implicitsResolutionFilter = scopeProcessor)
    }
)

fun getSimpleCompiler(
    compilationConfiguration: ScriptCompilationConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration,
): JupyterCompiler<KJvmReplCompilerBase<ReplCodeAnalyzerBase>> {
    return JupyterCompiler(SimpleReplCompiler(), compilationConfiguration, evaluationConfiguration)
}

fun getCompilerWithCompletion(
    compilationConfiguration: ScriptCompilationConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration,
): JupyterCompiler<KJvmReplCompilerWithIdeServices> {
    return JupyterCompiler(KJvmReplCompilerWithIdeServices(), compilationConfiguration, evaluationConfiguration)
}
