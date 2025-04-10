package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.repl.execution.BeforeCellExecutionsProcessor

/**
 * This class contains the cell code sent by the user "as-is" after any
 * [BeforeCellExecutionsProcessor] has run. These processors will remove
 * any file annotations and line magics. So it is only "real" Kotlin code
 * that will show up here.
 */
class EvalData(
    val executionCount: ExecutionCount,
    val rawCode: String,
) {
    constructor(evalRequestData: EvalRequestData) : this(evalRequestData.executionCount, evalRequestData.code)
}
