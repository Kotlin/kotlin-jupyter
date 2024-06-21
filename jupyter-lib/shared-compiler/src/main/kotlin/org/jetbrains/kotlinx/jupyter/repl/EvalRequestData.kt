package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount

class EvalRequestData(
    val code: Code,
    val executionCount: ExecutionCount = ExecutionCount.NO_COUNT,
    val storeHistory: Boolean = true,
    @Suppress("UNUSED")
    val isSilent: Boolean = false,
)
