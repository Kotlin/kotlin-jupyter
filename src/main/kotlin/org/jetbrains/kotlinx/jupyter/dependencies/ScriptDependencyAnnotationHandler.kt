package org.jetbrains.kotlinx.jupyter.dependencies

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration

interface ScriptDependencyAnnotationHandler {
    fun configure(
        configuration: ScriptCompilationConfiguration,
        annotations: List<Annotation>,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration>
}
