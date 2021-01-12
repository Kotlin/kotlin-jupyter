package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

interface TypeRenderersProcessor {

    fun renderResult(host: ExecutionHost, value: Any?, fieldName: String?): Any?

    fun register(renderer: RendererTypeHandler): Code?
}
