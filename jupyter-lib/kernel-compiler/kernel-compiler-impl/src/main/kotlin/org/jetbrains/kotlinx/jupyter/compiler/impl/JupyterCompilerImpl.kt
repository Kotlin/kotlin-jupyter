package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.jupyterOptions
import org.jetbrains.kotlinx.jupyter.config.toExecutionCount
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.getErrors
import org.jetbrains.kotlinx.jupyter.removeDuplicates
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.util.createCachedFun
import java.io.Closeable
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ReplCodeAnalyzer
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.api.analysisDiagnostics
import kotlin.script.experimental.api.repl
import kotlin.script.experimental.api.valueOr
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.util.toSourceCodePosition
import kotlin.script.experimental.util.LinkedSnippet
import kotlin.sequences.none
import kotlin.sequences.orEmpty
import kotlin.sequences.toList

open class JupyterCompilerImpl<CompilerT>(
    protected val compiler: CompilerT,
    protected val compilationConfig: ScriptCompilationConfiguration,
) : JupyterCompiler,
    Closeable
    where CompilerT : ReplCompiler<KJvmCompiledScript>,
          CompilerT : ReplCompleter,
          CompilerT : ReplCodeAnalyzer {
    override val version: KotlinKernelVersion = currentKernelVersion

    private val getCompilationConfiguration =
        createCachedFun { options: JupyterCompilingOptions ->
            compilationConfig.with {
                jupyterOptions(options)
                repl {
                    // This is also setting the $resX value
                    // It might be required for the new result value handling,
                    // if so, we need to track an always incrementing number
                    // on our end, which will be quite annoying.
                    // See KT-76172
                    // currentLineId(LineId(options.cellId.value, 0, 0))
                }
            }
        }

    override fun compileSync(
        snippetId: Int,
        code: String,
        options: JupyterCompilingOptions,
    ): LinkedSnippet<KJvmCompiledScript> {
        val snippet = SourceCodeImpl(snippetId, code)
        val compilationConfigWithJupyterOptions = getCompilationConfiguration(options)
        when (
            val resultWithDiagnostics =
                runBlocking { compiler.compile(snippet, compilationConfigWithJupyterOptions) }
        ) {
            is ResultWithDiagnostics.Failure -> {
                val metadata =
                    CellErrorMetaData(
                        options.cellId.toExecutionCount(),
                        code.lines().size,
                    )
                // Work-around for KT-74685
                val updatedDiagnostics = resultWithDiagnostics.removeDuplicates()
                throw ReplCompilerException(code, updatedDiagnostics, metadata = metadata)
            }

            is ResultWithDiagnostics.Success -> {
                // TODO "resultField" is null because in K2 the return value is no longer stored
                //  in a variable. This is breaking FieldHandler integration. We need to find a way
                //  to reference previous cells outputs using code so we can do something like `val x = notebook.outputs
                //  See KT-76172
                return resultWithDiagnostics.value
            }
        }
    }

    override fun complete(
        code: String,
        position: SourceCode.Position,
        snippetId: Int,
    ): List<SourceCodeCompletionVariant> {
        val sourceCode = SourceCodeImpl(snippetId, code)
        return runBlocking { compiler.complete(sourceCode, position, compilationConfig) }
            .valueOrNull()
            .orEmpty()
            .toList()
    }

    override fun checkComplete(
        code: Code,
        snippetId: Int,
    ): CheckCompletenessResult {
        val result = analyze(code, snippetId)
        val analysisResult = result.valueOr { throw ReplException(result.getErrors()) }
        val diagnostics = analysisResult[ReplAnalyzerResult.analysisDiagnostics]!!
        val isComplete = diagnostics.none { it.code == ScriptDiagnostic.incompleteCode }
        return CheckCompletenessResult(isComplete)
    }

    private fun analyze(
        code: Code,
        snippetId: Int,
    ): ResultWithDiagnostics<ReplAnalyzerResult> {
        val snippet = SourceCodeImpl(snippetId, code)

        return runBlocking {
            compiler.analyze(
                snippet,
                0.toSourceCodePosition(snippet),
                compilationConfig,
            )
        }
    }

    override fun listErrors(
        code: Code,
        snippetId: Int,
    ): Sequence<ScriptDiagnostic> {
        val result = analyze(code, snippetId).valueOrThrow()

        return result[ReplAnalyzerResult.analysisDiagnostics]!!
    }

    override fun close() {
    }
}
