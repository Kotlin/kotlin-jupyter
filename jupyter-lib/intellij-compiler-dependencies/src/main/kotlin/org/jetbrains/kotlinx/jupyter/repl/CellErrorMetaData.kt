package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount

/**
 * Helper class for tracking metadata needed to correctly enhance
 * errors that happens in user code as a consequence of executing
 * a cell.
 */
data class CellErrorMetaData(
    val executionCount: ExecutionCount,
    val linesOfUserSourceCode: Int,
)
