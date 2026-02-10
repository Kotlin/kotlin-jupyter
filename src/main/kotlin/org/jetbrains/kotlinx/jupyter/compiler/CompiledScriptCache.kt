package org.jetbrains.kotlinx.jupyter.compiler

import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResultDeserializer
import org.jetbrains.kotlinx.jupyter.config.CellId
import org.jetbrains.kotlinx.jupyter.config.toExecutionCount
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.repl.impl.CompilationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

internal class CompiledScriptCache {
    // Cache of compiled scripts by their hash code (computed on daemon side)
    private val scriptCache = mutableMapOf<Int, KJvmCompiledScript>()

    fun deserializeResult(
        originalCode: String,
        cellId: CellId,
        compileResult: CompileResult,
    ): CompilationResult =
        when (compileResult) {
            is CompileResult.Success -> {
                // Deserialize and cache scripts using CompileResultDeserializer
                val linkedSnippet = CompileResultDeserializer.deserialize(compileResult, scriptCache)
                CompilationResult(
                    linkedSnippet = linkedSnippet,
                    imports = compileResult.imports,
                    declarations = compileResult.declarations,
                )
            }
            is CompileResult.Failure -> {
                val failure = ResultWithDiagnostics.Failure(compileResult.diagnostics)
                throw ReplCompilerException(
                    originalCode,
                    failure,
                    metadata =
                        CellErrorMetaData(
                            executionCount = cellId.toExecutionCount(),
                            linesOfUserSourceCode = originalCode.lines().size,
                        ),
                )
            }
        }
}
