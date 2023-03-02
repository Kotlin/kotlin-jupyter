package org.jetbrains.kotlinx.jupyter.api

interface TextRenderersProcessor {
    fun register(renderer: TextRenderer, priority: Int = ProcessingPriority.DEFAULT)

    fun unregister(renderer: TextRenderer)

    fun render(value: Any?): String

    fun registeredRenderers(): List<TextRendererWithPriority>
}
