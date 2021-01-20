package org.jetbrains.kotlinx.jupyter.compiler

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration

interface CompilerArgsConfigurator {
    fun getArgs(): List<String>
    fun configure(configuration: ScriptCompilationConfiguration, annotations: List<Annotation>): ResultWithDiagnostics<ScriptCompilationConfiguration>
    fun addArg(arg: String)
}
