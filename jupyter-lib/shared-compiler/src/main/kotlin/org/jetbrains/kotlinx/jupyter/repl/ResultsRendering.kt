package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.textResult
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook

fun renderValue(
    notebook: MutableNotebook,
    executor: ExecutionHost,
    value: Any?,
): DisplayResult? {
    val renderedValue = notebook.renderersProcessor.renderValue(executor, value)
    return notebook.postRender(renderedValue)
}

fun MutableNotebook.postRender(value: Any?): DisplayResult? {
    fun renderAsText(obj: Any?): String = textRenderersProcessor.renderPreventingRecursion(obj)
    return when (value) {
        null -> textResult(renderAsText(null))
        is DisplayResult -> value
        is Renderable -> value.render(this)
        is Unit -> null
        else -> textResult(renderAsText(value))
    }
}
