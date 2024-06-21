package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount

class EvalData(
    val executionCount: ExecutionCount,
    val rawCode: String,
) {
    constructor(evalRequestData: EvalRequestData) : this(evalRequestData.executionCount, evalRequestData.code)
}
