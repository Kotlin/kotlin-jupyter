package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompiler
import org.jetbrains.kotlinx.jupyter.compiler.impl.JupyterCompilerImpl
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

internal fun getSimpleCompiler(
    compilationConfiguration: ScriptCompilationConfiguration,
): JupyterCompiler {
    class SimpleReplCompiler(
        hostConfiguration: ScriptingHostConfiguration,
    ) : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(hostConfiguration)

    return JupyterCompilerImpl(
        SimpleReplCompiler(
            compilationConfiguration[ScriptCompilationConfiguration.Companion.hostConfiguration]
                ?: defaultJvmScriptingHostConfiguration,
        ),
        compilationConfiguration,
    )
}