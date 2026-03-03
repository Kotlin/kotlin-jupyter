package org.jetbrains.kotlinx.jupyter.repl.result

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata

sealed interface EvalResultEx {
    val metadata: EvaluatedSnippetMetadata

    data class Success(
        val internalResult: InternalEvalResult,
        val renderedValue: Any?,
        val displayValue: DisplayResult?,
        override val metadata: EvaluatedSnippetMetadata,
    ) : EvalResultEx

    sealed interface AbstractError : EvalResultEx {
        val error: Throwable
    }

    data class Error(
        override val error: Throwable,
        override val metadata: EvaluatedSnippetMetadata,
    ) : AbstractError

    data class RenderedError(
        override val error: Throwable,
        val renderedError: Any?,
        val displayError: DisplayResult?,
        override val metadata: EvaluatedSnippetMetadata,
    ) : AbstractError

    data class Interrupted(
        override val metadata: EvaluatedSnippetMetadata,
    ) : EvalResultEx
}
