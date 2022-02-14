package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.messaging.OkResponseWithMessage
import org.jetbrains.kotlinx.jupyter.messaging.Response
import org.jetbrains.kotlinx.jupyter.messaging.toDisplayResult

data class InternalEvalResult(
    val result: FieldValue,
    val scriptInstance: Any,
)

data class EvalResult(
    val resultValue: Any?,
    val metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY
)

data class EvalResultEx(
    val rawValue: Any?,
    val renderedValue: Any?,
    val scriptInstance: Any,
    val resultFieldName: String?,
    val metadata: EvaluatedSnippetMetadata,
)

fun rawToResponse(value: Any?, notebook: Notebook, metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY): Response {
    return OkResponseWithMessage(value.toDisplayResult(notebook), metadata)
}

fun EvalResult.toResponse(notebook: Notebook): Response {
    return rawToResponse(resultValue, notebook, metadata)
}
