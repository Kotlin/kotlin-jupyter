package org.jetbrains.kotlinx.jupyter.compiler

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.JavaRuntime
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.asSuccess

class DefaultCompilerArgsConfigurator(
    jvmTargetVersion: String = JavaRuntime.version,
) : CompilerArgsConfigurator {
    private val argsList =
        mutableListOf(
            "-jvm-target",
            jvmTargetVersion,
            "-no-stdlib",
            "-Xskip-metadata-version-check",
        )

    override fun getArgs(): List<String> {
        return argsList.toList()
    }

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
