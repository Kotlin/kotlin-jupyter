package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext

/**
 * Interface responsible for handling file level annotations inside a notebook cell.
 * This is e.g., annotations like `@file:DependsOn("...")`, `@file:Repository("...")` and
 * `@file:CompilerArgs("...")`.
 *
 * This interface is a subcomponent of [org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter].
 */
interface FileAnnotationsProcessor {
    fun register(handler: FileAnnotationHandler)

    fun process(
        context: ScriptConfigurationRefinementContext,
        host: KotlinKernelHost,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration>
}
