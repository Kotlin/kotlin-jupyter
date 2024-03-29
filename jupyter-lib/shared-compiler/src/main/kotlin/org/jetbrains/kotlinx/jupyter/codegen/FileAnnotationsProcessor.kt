package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext

interface FileAnnotationsProcessor {
    fun register(handler: FileAnnotationHandler)

    fun process(
        context: ScriptConfigurationRefinementContext,
        host: KotlinKernelHost,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration>
}
