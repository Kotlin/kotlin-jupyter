package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.getErrors
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.analysisDiagnostics
import kotlin.script.experimental.api.valueOr
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.util.toSourceCodePosition

internal class JupyterCompilerWithCompletionImpl(
    compiler: K2KJvmReplCompilerWithCompletion,
    compilationConfig: ScriptCompilationConfiguration,
    evaluationConfig: ScriptEvaluationConfiguration,
) : JupyterCompilerImpl<K2KJvmReplCompilerWithCompletion>(compiler, compilationConfig, evaluationConfig),
    JupyterCompilerWithCompletion {
    override val completer: ReplCompleter
        get() = compiler

    override fun checkComplete(code: Code): CheckCompletenessResult {
        val result = analyze(code)
        val analysisResult = result.valueOr { throw ReplException(result.getErrors()) }
        val diagnostics = analysisResult[ReplAnalyzerResult.analysisDiagnostics]!!
        val isComplete = diagnostics.none { it.code == ScriptDiagnostic.incompleteCode }
        return CheckCompletenessResult(isComplete)
    }

    private fun analyze(code: Code): ResultWithDiagnostics<ReplAnalyzerResult> {
        val snippet = SourceCodeImpl(nextCounter(), code)

        return runBlocking {
            compiler.analyze(
                snippet,
                0.toSourceCodePosition(snippet),
                compilationConfig,
            )
        }
    }

    override fun listErrors(code: Code): Sequence<ScriptDiagnostic> {
        val result = analyze(code).valueOrThrow()

        return result[ReplAnalyzerResult.analysisDiagnostics]!!
    }
}
