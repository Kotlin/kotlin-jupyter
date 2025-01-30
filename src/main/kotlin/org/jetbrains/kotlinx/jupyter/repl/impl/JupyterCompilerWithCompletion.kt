package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.slf4j.Logger
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

internal interface JupyterCompilerWithCompletion : JupyterCompiler {
    val completer: ReplCompleter

    fun checkComplete(code: Code): CheckCompletenessResult

    fun listErrors(code: Code): Sequence<ScriptDiagnostic>

    companion object {
        fun create(
            disposable: Disposable,
            compilationConfiguration: ScriptCompilationConfiguration,
            evaluationConfiguration: ScriptEvaluationConfiguration,
        ): JupyterCompilerWithCompletion {
            return JupyterCompilerWithCompletionImpl(
                K2KJvmReplCompilerWithCompletion(
                    hostConfiguration = compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration] ?: defaultJvmScriptingHostConfiguration,
                    // Kotlin Stdlib is required to be on the classpath before the first snippet is executed. Is this intentional?
                    compilerConfiguration = compilationConfiguration
                ),
                compilationConfiguration,
                evaluationConfiguration,
            )
        }
    }
}
