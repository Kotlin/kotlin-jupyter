package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.MutableNotebook
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.textResult
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.messaging.OkResponseWithMessage
import org.jetbrains.kotlinx.jupyter.messaging.Response

data class InternalEvalResult(
    val result: FieldValue,
    val scriptInstance: Any,
)

data class ShutdownEvalResult(
    val resultValue: Any?,
    val metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY,
)

data class EvalResultEx(
    val rawValue: Any?,
    val renderedValue: Any?,
    val displayValue: DisplayResult?,
    val scriptInstance: Any,
    val resultFieldName: String?,
    val metadata: EvaluatedSnippetMetadata,
)

fun rawToResponse(value: DisplayResult?, metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY): Response {
    return OkResponseWithMessage(value, metadata)
}

fun renderValue(notebook: MutableNotebook, executor: ExecutionHost, value: Any?): DisplayResult? {
    return notebook.postRender(notebook.renderersProcessor.renderValue(executor, value))
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

fun Any?.toDisplayResult(notebook: Notebook): DisplayResult? = when (this) {
    null -> textResult("null")
    is DisplayResult -> this
    is Renderable -> this.render(notebook)
    is Unit -> null
    else -> textResult(this.toString())
}
