package org.jetbrains.kotlinx.jupyter.compiler.impl

import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompilerWithCompletion
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

object JupyterCompilerFactory {
    fun createK2Compiler(compilationConfiguration: ScriptCompilationConfiguration): JupyterCompilerWithCompletion =
        K2JupyterCompilerWithCompletionImpl(
            K2KJvmReplCompilerWithCompletion(
                hostConfiguration =
                    compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                        ?: defaultJvmScriptingHostConfiguration,
                // Kotlin Stdlib is required to be on the classpath before the first snippet is executed. Is this intentional?
                compilerConfiguration = compilationConfiguration,
            ),
            compilationConfiguration,
        )

    fun createK1Compiler(compilationConfiguration: ScriptCompilationConfiguration): JupyterCompilerWithCompletion =
        K1JupyterCompilerWithCompletionImpl(
            KJvmReplCompilerWithIdeServices(
                compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                    ?: defaultJvmScriptingHostConfiguration,
            ),
            compilationConfiguration,
        )
}
