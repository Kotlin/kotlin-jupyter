package org.jetbrains.kotlinx.jupyter.repl.impl

import jupyter.kotlin.KotlinKernelHostProvider
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.execution.InterruptionCallbacksProcessor

class InterruptionCallbacksProcessorImpl(
    private val hostProvider: KotlinKernelHostProvider
) : InterruptionCallbacksProcessor {
    private val callbacks = mutableListOf<InterruptionCallback>()

    override fun runCallbacks() {
        callbacks.forEach { it.invoke(hostProvider.host!!) }
    }

    override fun register(callback: InterruptionCallback) {
        callbacks.add(callback)
    }
}
