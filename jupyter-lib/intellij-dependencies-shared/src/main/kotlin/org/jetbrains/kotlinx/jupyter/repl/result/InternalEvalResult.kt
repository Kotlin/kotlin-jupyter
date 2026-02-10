package org.jetbrains.kotlinx.jupyter.repl.result

import org.jetbrains.kotlinx.jupyter.api.FieldValue

/**
 * This class wraps the low-level output of evaluating a single snippet in the Kotlin compiler.
 */
data class InternalEvalResult(
    /**
     * The output of a cell, if any.
     * If the cell had no output, `FieldValue.value` is `Unit` and `name` is `null`.
     */
    val result: FieldValue,
    /**
     * A reference to the instance of the snippet used for evaluation.
     */
    val scriptInstance: Any,
)
