package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

interface TypeRenderersProcessor {
    fun renderResult(value: Any?, fieldName: String?): Any?

    fun register(renderer: RendererTypeHandler): Code?
}
