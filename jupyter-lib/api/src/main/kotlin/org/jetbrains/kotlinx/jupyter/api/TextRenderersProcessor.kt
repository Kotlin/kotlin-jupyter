package org.jetbrains.kotlinx.jupyter.api

interface TextRenderersProcessor {
    fun register(renderer: TextRenderer, priority: Int = RendererPriority.DEFAULT)

    fun unregister(renderer: TextRenderer)

    fun render(value: Any?): String

    fun registeredRenderers(): List<TextRendererWithPriority>
}
