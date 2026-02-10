package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount

// Extension function to convert CellId to ExecutionCount
fun CellId.toExecutionCount(): ExecutionCount = ExecutionCount(this.value + 1)

// Extension function to convert ExecutionCount to CellId
fun ExecutionCount.toCellId(): CellId = CellId(this.value - 1)
