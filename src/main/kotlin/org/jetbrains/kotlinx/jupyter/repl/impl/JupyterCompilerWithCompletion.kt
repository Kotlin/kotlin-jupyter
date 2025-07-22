package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.repl.impl.k1.K1JupyterCompilerWithCompletionImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.k2.K2JupyterCompilerWithCompletionImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.k2.K2KJvmReplCompilerWithCompletion
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
        fun createK2Compiler(
            compilationConfiguration: ScriptCompilationConfiguration,
            evaluationConfiguration: ScriptEvaluationConfiguration,
        ): JupyterCompilerWithCompletion =
            K2JupyterCompilerWithCompletionImpl(
                K2KJvmReplCompilerWithCompletion(
                    hostConfiguration =
                        compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                            ?: defaultJvmScriptingHostConfiguration,
                    // Kotlin Stdlib is required to be on the classpath before the first snippet is executed. Is this intentional?
                    compilerConfiguration = compilationConfiguration,
                ),
                compilationConfiguration,
                evaluationConfiguration,
            )

        fun createK1Compiler(
            compilationConfiguration: ScriptCompilationConfiguration,
            evaluationConfiguration: ScriptEvaluationConfiguration,
        ): JupyterCompilerWithCompletion =
            K1JupyterCompilerWithCompletionImpl(
                KJvmReplCompilerWithIdeServices(
                    compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                        ?: defaultJvmScriptingHostConfiguration,
                ),
                compilationConfiguration,
                evaluationConfiguration,
            )
    }
}
