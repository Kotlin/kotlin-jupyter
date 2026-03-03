package org.jetbrains.kotlinx.jupyter.execution

import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback

interface InterruptionCallbacksProcessor : ExtensionsProcessor<InterruptionCallback> {
    fun runCallbacks()
}
