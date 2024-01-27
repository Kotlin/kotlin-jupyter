package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.FieldValue

data class InternalEvalResult(
    val result: FieldValue,
    val scriptInstance: Any,
)
