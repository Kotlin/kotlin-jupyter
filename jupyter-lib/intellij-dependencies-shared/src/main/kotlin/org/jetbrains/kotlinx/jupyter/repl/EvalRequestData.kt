package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount

/**
 * This class wraps the cell sent by the user for evaluation.
 */
class EvalRequestData(
    /**
     * Unprocessed user cell content.
     */
    val code: Code,
    val executionCount: ExecutionCount = ExecutionCount.NO_COUNT,
    /**
     * If `false` the [code] is executed, but the cell and its result will not be
     * visible to the user, and it will not be tracked by [org.jetbrains.kotlinx.jupyter.api.Notebook].
     */
    val storeHistory: Boolean = true,
    @Suppress("UNUSED")
    val isSilent: Boolean = false,
)
