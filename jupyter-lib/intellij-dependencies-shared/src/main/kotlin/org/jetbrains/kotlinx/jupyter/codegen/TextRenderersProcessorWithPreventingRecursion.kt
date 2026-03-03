package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.TextRenderersProcessor

interface TextRenderersProcessorWithPreventingRecursion : TextRenderersProcessor {
    fun renderPreventingRecursion(value: Any?): String
}
