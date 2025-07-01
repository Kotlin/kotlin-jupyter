package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

internal fun getSimpleCompiler(
    compilationConfiguration: ScriptCompilationConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration,
): JupyterCompiler {
    class SimpleReplCompiler(
        hostConfiguration: ScriptingHostConfiguration,
    ) : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(hostConfiguration)

    return JupyterCompilerImpl(
        SimpleReplCompiler(
            compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                ?: defaultJvmScriptingHostConfiguration,
        ),
        compilationConfiguration,
        evaluationConfiguration,
    )
}
