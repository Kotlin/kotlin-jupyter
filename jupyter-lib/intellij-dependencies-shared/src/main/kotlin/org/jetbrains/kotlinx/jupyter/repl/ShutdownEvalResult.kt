package org.jetbrains.kotlinx.jupyter.repl

data class ShutdownEvalResult(
    val resultValue: Any?,
    val metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY,
)
