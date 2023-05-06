package org.jetbrains.kotlinx.jupyter.api

interface TextRenderersProcessor : ExtensionsProcessor<TextRenderer> {
    fun render(value: Any?): String

    fun registeredRenderers(): List<TextRendererWithPriority>
}
