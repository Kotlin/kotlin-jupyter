package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.getScriptCollectedData
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.util.actualClassLoader
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.jupyterOptions
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.util.createCachedFun
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.refineConfigurationBeforeCompiling
import kotlin.script.experimental.api.refineOnAnnotations
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.lastSnippetClassLoader
import kotlin.script.experimental.jvm.updateClasspath

internal open class JupyterCompilerImpl<CompilerT : ReplCompiler<KJvmCompiledScript>>(
    protected val compiler: CompilerT,
    initialCompilationConfig: ScriptCompilationConfiguration,
    private val basicEvaluationConfiguration: ScriptEvaluationConfiguration,
) : JupyterCompiler {

    private val executionCounter = AtomicInteger()
    private val classes = mutableListOf<KClass<*>>()

    private val refinementCallbacks =
        mutableListOf<(ScriptConfigurationRefinementContext) -> ResultWithDiagnostics<ScriptCompilationConfiguration>>()

    protected val compilationConfig: ScriptCompilationConfiguration =
        initialCompilationConfig.with {
            refineConfiguration {
                val handlers = initialCompilationConfig[ScriptCompilationConfiguration.refineConfigurationBeforeCompiling].orEmpty()
                handlers.forEach { beforeCompiling(it.handler) }

                beforeCompiling(::updateConfig)
            }
        }

    override val version: KotlinKernelVersion = currentKernelVersion

    override val numberOfSnippets: Int
        get() = classes.size

    override val previousScriptsClasses: List<KClass<*>>
        get() = classes

    override val lastKClass: KClass<*>
        get() = classes.last()

    private val _lastClassLoader: ClassLoader
        get() = basicEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]!!

    override val lastClassLoader: ClassLoader
        get() = classes.lastOrNull()?.java?.classLoader ?: _lastClassLoader

    override fun nextCounter() = executionCounter.getAndIncrement()

    override fun updateCompilationConfig(body: ScriptCompilationConfiguration.Builder.() -> Unit) {
        refinementCallbacks.add { context ->
            context.compilationConfiguration.with(body).asSuccess()
        }
    }

    override fun updateCompilationConfigOnAnnotation(
        handler: FileAnnotationHandler,
        callback: (ScriptConfigurationRefinementContext) -> ResultWithDiagnostics<ScriptCompilationConfiguration>,
    ) {
        refinementCallbacks.add { context ->
            val ktFile = (context.script as KtFileScriptSource).ktFile

            val withImport =
                context.compilationConfiguration.with {
                    defaultImports(handler.annotation.java.name)
                    refineConfiguration {
                        onAnnotations(KotlinType(handler.annotation.qualifiedName!!)) {
                            callback(it)
                        }
                    }
                }
            val collectedData = getScriptCollectedData(ktFile, withImport, ktFile.project, lastClassLoader)

            withImport.refineOnAnnotations(context.script, collectedData)
        }
    }

    private fun updateConfig(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        return refinementCallbacks.fold(
            context.compilationConfiguration.asSuccess(),
        ) { config: ResultWithDiagnostics<ScriptCompilationConfiguration>, callback ->
            config.valueOrNull()?.let { conf ->
                callback(
                    ScriptConfigurationRefinementContext(
                        context.script,
                        conf,
                        context.collectedData,
                    ),
                )
            } ?: config
        }
    }

    private val getCompilationConfiguration =
        createCachedFun { options: JupyterCompilingOptions ->
            compilationConfig.with {
                jupyterOptions(options)
                // Work-around for https://youtrack.jetbrains.com/issue/KT-74768/K2-Repl-refineConfiguration-does-not-update-the-classpath-correctly
                updateClasspath(options.classpathFromAnnotations)
            }
        }

    override fun compileSync(
        snippet: SourceCode,
        options: JupyterCompilingOptions,
    ): JupyterCompiler.Result {
        val compilationConfigWithJupyterOptions = getCompilationConfiguration(options)
        when (val resultWithDiagnostics = runBlocking {
            compiler.compile(snippet, compilationConfigWithJupyterOptions)
        }) {
            is ResultWithDiagnostics.Failure -> {
                val metadata =
                    CellErrorMetaData(
                        options.cellId.toExecutionCount(),
                        snippet.text.lines().size,
                    )
                throw ReplCompilerException(snippet.text, resultWithDiagnostics, metadata = metadata)
            }
            is ResultWithDiagnostics.Success -> {
                val result = resultWithDiagnostics.value
                val compiledScript = result.get()

                val configWithClassloader =
                    basicEvaluationConfiguration.with {
                        jvm {
                            lastSnippetClassLoader(lastClassLoader)
                            baseClassLoader(_lastClassLoader.parent)
                        }
                    }
                val classLoader = compiledScript.getOrCreateActualClassloader(configWithClassloader)
                val newEvaluationConfiguration =
                    configWithClassloader.with {
                        jvm {
                            actualClassLoader(classLoader)
                        }
                    }

                when (val kClassWithDiagnostics = runBlocking { compiledScript.getClass(newEvaluationConfiguration) }) {
                    is ResultWithDiagnostics.Failure -> throw ReplCompilerException(snippet.text, kClassWithDiagnostics)
                    is ResultWithDiagnostics.Success -> {
                        val kClass = kClassWithDiagnostics.value
                        classes.add(kClass)
                        return JupyterCompiler.Result(result, newEvaluationConfiguration)
                    }
                    else -> throw IllegalStateException("Impossible value: $kClassWithDiagnostics")
                }
            }
        }
    }
}

