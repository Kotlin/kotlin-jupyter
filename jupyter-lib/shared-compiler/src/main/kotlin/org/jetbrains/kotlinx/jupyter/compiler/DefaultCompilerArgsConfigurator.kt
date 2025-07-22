package org.jetbrains.kotlinx.jupyter.compiler

import jupyter.kotlin.CompilerArgs
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.asSuccess

/**
 * This class defines the default compiler arguments used to compile snippets.
 *
 * @see org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
 */
class DefaultCompilerArgsConfigurator(
    jvmTargetVersion: String,
    extraArgs: List<String> = emptyList(),
) : CompilerArgsConfigurator {
    private val argsList =
        mutableListOf(
            "-jvm-target",
            jvmTargetVersion,
            // Ignore the Compose plugin if the Compose runtime isn't on the classpath
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:skipIrLoweringIfRuntimeNotFound=true",
        ).apply {
            addAll(extraArgs)
        }

    override fun getArgs(): List<String> = argsList.toList()

    override fun configure(
        configuration: ScriptCompilationConfiguration,
        annotations: List<Annotation>,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        annotations.forEach {
            when (it) {
                is CompilerArgs -> argsList.addAll(it.values)
            }
        }

        return configuration.asSuccess()
    }

    override fun addArg(arg: String) {
        argsList.add(arg)
    }
}
