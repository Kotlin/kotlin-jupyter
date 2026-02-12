package org.jetbrains.kotlinx.jupyter.compiler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.Diagnostic
import org.jetbrains.kotlinx.jupyter.compiler.util.actualClassLoader
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.toExecutionCount
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.repl.impl.JupyterCompiler
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCode.Location
import kotlin.script.experimental.api.SourceCode.Position
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.lastSnippetClassLoader

/**
 * Adapter that wraps CompilerService to implement JupyterCompiler interface.
 * This allows using the new RPC-based compiler service with the existing evaluation pipeline.
 */
internal class CompilerServiceAdapter(
    private val compilerService: CompilerService,
    private val basicEvaluationConfiguration: ScriptEvaluationConfiguration,
) : JupyterCompiler {
    private val executionCounter = AtomicInteger()
    private val classes = mutableListOf<KClass<*>>()

    private val _lastClassLoader: ClassLoader
        get() = classes.lastOrNull()?.java?.classLoader
            ?: basicEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]!!

    override val numberOfSnippets: Int
        get() = classes.size

    override val previousScriptsClasses: List<KClass<*>>
        get() = classes

    override val lastKClass: KClass<*>
        get() = classes.last()

    override val lastClassLoader: ClassLoader
        get() = classes.lastOrNull()?.java?.classLoader ?: _lastClassLoader

    override fun nextCounter(): Int = executionCounter.getAndIncrement()

    override val version: KotlinKernelVersion
        get() = currentKernelVersion

    override fun updateCompilationConfig(body: ScriptCompilationConfiguration.Builder.() -> Unit) {
        // CompilerService manages its own compilation configuration
        // This is a no-op for the adapter, as config updates would need to be sent via RPC
        // In the future, this could be implemented by sending configuration updates to the CompilerService
    }

    override fun updateCompilationConfigOnAnnotation(
        handler: FileAnnotationHandler,
        callback: (ScriptConfigurationRefinementContext) -> ResultWithDiagnostics<ScriptCompilationConfiguration>,
    ) {
        // CompilerService manages its own annotation handling
        // This is a no-op for the adapter
        // In the future, this could be implemented by registering handlers with the CompilerService
    }

    /**
     * Compile code using the CompilerService and return the result in a format
     * compatible with the existing evaluation pipeline.
     */
    override fun compileSync(
        snippet: SourceCode,
        options: JupyterCompilingOptions,
    ): JupyterCompiler.Result {
        val snippetId = executionCounter.get()
        val cellId = options.cellId.value

        val compileResult = runBlocking {
            compilerService.compile(snippetId, snippet.text, cellId)
        }

        return when (compileResult) {
            is CompileResult.Success -> {
                val linkedSnippet = CompileResultDeserializer.deserialize(compileResult)
                val compiledScript = linkedSnippet.get()

                // Create evaluation configuration similar to JupyterCompilerImpl
                val configWithClassloader = basicEvaluationConfiguration.with {
                    jvm {
                        lastSnippetClassLoader(lastClassLoader)
                        baseClassLoader(_lastClassLoader.parent)
                    }
                }

                val classLoader = compiledScript.getOrCreateActualClassloader(configWithClassloader)
                val newEvaluationConfiguration = configWithClassloader.with {
                    jvm {
                        actualClassLoader(classLoader)
                    }
                }

                // Get the class and track it
                val kClassResult = runBlocking {
                    compiledScript.getClass(newEvaluationConfiguration)
                }

                when (kClassResult) {
                    is ResultWithDiagnostics.Success -> {
                        val kClass = kClassResult.value
                        classes.add(kClass)
                    }
                    is ResultWithDiagnostics.Failure -> {
                        val metadata = CellErrorMetaData(
                            options.cellId.toExecutionCount(),
                            snippet.text.lines().size,
                        )
                        throw ReplCompilerException(snippet.text, kClassResult, metadata = metadata)
                    }
                }

                JupyterCompiler.Result(linkedSnippet, newEvaluationConfiguration)
            }
            is CompileResult.Failure -> {
                val metadata = CellErrorMetaData(
                    options.cellId.toExecutionCount(),
                    snippet.text.lines().size,
                )
                // Convert diagnostics to ResultWithDiagnostics.Failure format
                val scriptDiagnostics = compileResult.diagnostics.map { it.toScriptDiagnostic() }
                val failure = ResultWithDiagnostics.Failure(scriptDiagnostics)
                throw ReplCompilerException(snippet.text, failure, metadata = metadata)
            }
        }
    }

    private fun Diagnostic.toScriptDiagnostic(): ScriptDiagnostic {
        val severity = when (this.severity) {
            Diagnostic.Severity.ERROR -> ScriptDiagnostic.Severity.ERROR
            Diagnostic.Severity.WARNING -> ScriptDiagnostic.Severity.WARNING
            Diagnostic.Severity.INFO -> ScriptDiagnostic.Severity.INFO
        }
        val location = if (line != null && column != null) {
            Location(Position(line!!, column!!), null)
        } else {
            null
        }
        return ScriptDiagnostic(
            ScriptDiagnostic.unspecifiedError,
            message,
            severity,
            location = location,
        )
    }
}
