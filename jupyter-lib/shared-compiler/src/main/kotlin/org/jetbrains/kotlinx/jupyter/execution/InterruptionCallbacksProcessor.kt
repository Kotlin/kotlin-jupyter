package org.jetbrains.kotlinx.jupyter.execution

import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback

interface InterruptionCallbacksProcessor {
    fun runCallbacks()

    fun register(callback: InterruptionCallback)
}
