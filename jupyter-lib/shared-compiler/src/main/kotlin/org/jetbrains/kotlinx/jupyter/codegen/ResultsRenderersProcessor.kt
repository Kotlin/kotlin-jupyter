package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.PrecompiledRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.RendererFieldHandler
import org.jetbrains.kotlinx.jupyter.api.RenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

interface ResultsRenderersProcessor : RenderersProcessor {
    /**
     * Renders cell result [field] represented as [FieldValue] in the [host] context
     */
    fun renderResult(host: ExecutionHost, field: FieldValue): Any?

    /**
     * Adds new [renderer] for this notebook.
     * Returns code to be executed on execution host
     * for [PrecompiledRendererTypeHandler]'s.
     */
    fun register(renderer: RendererFieldHandler): Code?

    fun register(renderer: RendererFieldHandler, priority: Int): Code?
}
