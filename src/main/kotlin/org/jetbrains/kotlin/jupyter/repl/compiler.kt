package org.jetbrains.kotlin.jupyter.repl

import org.jetbrains.kotlin.jupyter.compiler.JupyterCompiler
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

fun getCompilerWithCompletion(
    compilationConfiguration: ScriptCompilationConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration,
): JupyterCompiler<KJvmReplCompilerWithIdeServices> {
    return JupyterCompiler(
        KJvmReplCompilerWithIdeServices(
            compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                ?: defaultJvmScriptingHostConfiguration
        ),
        compilationConfiguration,
        evaluationConfiguration
    )
}
