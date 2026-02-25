package org.jetbrains.kotlinx.jupyter.compiler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResultDeserializer
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.toExecutionCount
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.repl.CompleteFunction
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompilerWithCompletion
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

/**
 * Adapter that wraps CompilerService to implement JupyterCompilerWithCompletion interface.
 * This allows using the new RPC-based compiler service with the existing evaluation pipeline.
 */
internal class CompilerServiceAdapter(
    private val compilerService: CompilerService,
) : JupyterCompilerWithCompletion {
    private val executionCounter = AtomicInteger()

    // Cache of compiled scripts by their hash code (computed on daemon side)
    private val scriptCache = mutableMapOf<Int, KJvmCompiledScript>()

    override fun nextCounter(): Int = executionCounter.getAndIncrement()

    override val version: KotlinKernelVersion
        get() = currentKernelVersion

    /**
     * Compile code using the CompilerService and return the compiled LinkedSnippet.
     * Uses a cache to avoid deserializing the same scripts multiple times.
     */
    override fun compileSync(
        snippet: SourceCode,
        options: JupyterCompilingOptions,
    ): LinkedSnippet<KJvmCompiledScript> {
        val snippetId = executionCounter.get()
        val cellId = options.cellId.value

        val compileResult = runBlocking {
            compilerService.compile(snippetId, snippet.text, cellId)
        }

        return when (compileResult) {
            is CompileResult.Success -> {
                // Deserialize and cache scripts using CompileResultDeserializer
                CompileResultDeserializer.deserialize(compileResult, scriptCache)
            }
            is CompileResult.Failure -> {
                val metadata = CellErrorMetaData(
                    options.cellId.toExecutionCount(),
                    snippet.text.lines().size,
                )
                val failure = ResultWithDiagnostics.Failure(compileResult.diagnostics)
                throw ReplCompilerException(snippet.text, failure, metadata = metadata)
            }
        }
    }

    override val complete: CompleteFunction = { code, position ->
        val id = executionCounter.get()
        val completions = runBlocking {
            compilerService.complete(code.text, id, position)
        }
        completions.asSequence().asSuccess()
    }

    override fun checkComplete(code: Code): CheckCompletenessResult {
        val isComplete = runBlocking {
            compilerService.checkComplete(code)
        }
        return CheckCompletenessResult(isComplete)
    }

    override fun listErrors(code: Code): Sequence<ScriptDiagnostic> {
        val id = executionCounter.get()
        val diagnostics = runBlocking {
            compilerService.listErrors(code, id)
        }
        return diagnostics.asSequence()
    }
}
