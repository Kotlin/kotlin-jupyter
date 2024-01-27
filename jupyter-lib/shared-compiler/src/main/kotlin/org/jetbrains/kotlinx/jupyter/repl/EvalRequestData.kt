package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code

class EvalRequestData(
    val code: Code,
    val jupyterId: Int = -1,
    val storeHistory: Boolean = true,
    @Suppress("UNUSED")
    val isSilent: Boolean = false,
)
