package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

/**
 * [TypeRenderersProcessor] is responsible for rendering objects.
 * You may use it to render values exactly like notebook renders results,
 * and also register new renderers in runtime.
 */
interface TypeRenderersProcessor {
    /**
     * Renders [value] in context of this execution [host]
     */
    fun renderValue(host: ExecutionHost, value: Any?): Any?

    /**
     * Adds new [renderer] for this notebook.
     * Don't turn on the optimizations for [PrecompiledRendererTypeHandler]
     */
    fun registerWithoutOptimizing(renderer: RendererTypeHandler)
}
