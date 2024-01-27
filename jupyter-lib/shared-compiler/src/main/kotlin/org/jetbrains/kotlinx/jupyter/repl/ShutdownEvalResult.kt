package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata

data class ShutdownEvalResult(
    val resultValue: Any?,
    val metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY,
)
