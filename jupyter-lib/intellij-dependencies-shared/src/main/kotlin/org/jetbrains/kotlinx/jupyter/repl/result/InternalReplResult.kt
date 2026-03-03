package org.jetbrains.kotlinx.jupyter.repl.result

sealed interface InternalReplResult {
    val metadata: InternalMetadata

    class Success(
        val internalResult: InternalEvalResult,
        override val metadata: InternalMetadata,
    ) : InternalReplResult

    class Error(
        val error: Throwable,
        override val metadata: InternalMetadata,
    ) : InternalReplResult
}
