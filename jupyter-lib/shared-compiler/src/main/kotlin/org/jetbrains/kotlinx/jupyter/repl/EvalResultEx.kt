package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata

data class EvalResultEx(
    val rawValue: Any?,
    val renderedValue: Any?,
    val displayValue: DisplayResult?,
    val scriptInstance: Any,
    val resultFieldName: String?,
    val metadata: EvaluatedSnippetMetadata,
)
