package org.jetbrains.kotlinx.jupyter.api

interface TextRenderersProcessor {
    fun register(renderer: TextRenderer, priority: Int = RendererPriority.DEFAULT)

    fun render(value: Any?): String
}
