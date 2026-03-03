package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompilerWithCompletion
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.exceptions.getErrors
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.repl.CompleteFunction
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.analysisDiagnostics
import kotlin.script.experimental.api.valueOr
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.util.toSourceCodePosition

/**
 * Currently just a copy of [K2JupyterCompilerWithCompletionImpl] with the only difference being the type-parameter
 * of [compiler]. The reason is that we cannot create a unified type-abstraction for both of them right now, due to
 * [org.jetbrains.kotlinx.jupyter.repl.impl.k2.K2KJvmReplCompilerWithCompletion] being located in the Kotlin Kernel and [org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices]
 * being inside the compiler
 */
internal class K1JupyterCompilerWithCompletionImpl(
    compiler: KJvmReplCompilerWithIdeServices,
    compilationConfig: ScriptCompilationConfiguration,
) : JupyterCompilerImpl<KJvmReplCompilerWithIdeServices>(compiler, compilationConfig),
    JupyterCompilerWithCompletion {
    override val complete: CompleteFunction = CompleteFunction { code, cursor, snippetId ->
        val sourceCode = SourceCodeImpl(snippetId, code)
        compiler.complete(sourceCode, cursor, compilationConfig)
    }

    override fun checkComplete(code: Code, snippetId: Int): CheckCompletenessResult {
        val result = analyze(code, snippetId)
        val analysisResult = result.valueOr { throw ReplException(result.getErrors()) }
        val diagnostics = analysisResult[ReplAnalyzerResult.Companion.analysisDiagnostics]!!
        val isComplete = diagnostics.none { it.code == ScriptDiagnostic.Companion.incompleteCode }
        return CheckCompletenessResult(isComplete)
    }

    private fun analyze(code: Code, snippetId: Int): ResultWithDiagnostics<ReplAnalyzerResult> {
        val snippet = SourceCodeImpl(snippetId, code)

        return runBlocking {
            compiler.analyze(
                snippet,
                0.toSourceCodePosition(snippet),
                compilationConfig,
            )
        }
    }

    override fun listErrors(code: Code, snippetId: Int): Sequence<ScriptDiagnostic> {
        val result = analyze(code, snippetId).valueOrThrow()

        return result[ReplAnalyzerResult.Companion.analysisDiagnostics]!!
    }
}
