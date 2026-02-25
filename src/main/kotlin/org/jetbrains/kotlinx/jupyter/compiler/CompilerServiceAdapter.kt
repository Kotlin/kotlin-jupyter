package org.jetbrains.kotlinx.jupyter.compiler

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResultDeserializer
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompilerWithCompletion
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.toExecutionCount
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.repl.CompleteFunction
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
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
    // Cache of compiled scripts by their hash code (computed on daemon side)
    private val scriptCache = mutableMapOf<Int, KJvmCompiledScript>()

    override val version: KotlinKernelVersion
        get() = currentKernelVersion

    /**
     * Compile code using the CompilerService and return the compiled LinkedSnippet.
     * Uses a cache to avoid deserializing the same scripts multiple times.
     */
    override fun compileSync(
        snippetId: Int,
        code: String,
        options: JupyterCompilingOptions,
    ): LinkedSnippet<KJvmCompiledScript> {
        val cellId = options.cellId.value

        val compileResult = runBlocking {
            compilerService.compile(snippetId, code, cellId, options.isUserCode)
        }

        return when (compileResult) {
            is CompileResult.Success -> {
                // Deserialize and cache scripts using CompileResultDeserializer
                CompileResultDeserializer.deserialize(compileResult, scriptCache)
            }
            is CompileResult.Failure -> {
                val metadata = CellErrorMetaData(
                    options.cellId.toExecutionCount(),
                    code.lines().size,
                )
                val failure = ResultWithDiagnostics.Failure(compileResult.diagnostics)
                throw ReplCompilerException(code, failure, metadata = metadata)
            }
        }
    }

    override val complete: CompleteFunction = CompleteFunction { code, position, snippetId ->
        val completions = runBlocking {
            compilerService.complete(code, snippetId, position)
        }
        completions.asSequence().asSuccess()
    }

    override fun checkComplete(code: Code, snippetId: Int): CheckCompletenessResult {
        val isComplete = runBlocking {
            compilerService.checkComplete(code, snippetId)
        }
        return CheckCompletenessResult(isComplete)
    }

    override fun listErrors(code: Code, snippetId: Int): Sequence<ScriptDiagnostic> {
        val diagnostics = runBlocking {
            compilerService.listErrors(code, snippetId)
        }
        return diagnostics.asSequence()
    }
}
