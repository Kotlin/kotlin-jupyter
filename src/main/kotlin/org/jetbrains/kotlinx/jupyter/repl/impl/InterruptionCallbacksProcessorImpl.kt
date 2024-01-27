package org.jetbrains.kotlinx.jupyter.repl.impl

import jupyter.kotlin.providers.KotlinKernelHostProvider
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.execution.InterruptionCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.repl.execution.AbstractExtensionsProcessor

class InterruptionCallbacksProcessorImpl(
    private val hostProvider: KotlinKernelHostProvider,
) : AbstractExtensionsProcessor<InterruptionCallback>(), InterruptionCallbacksProcessor {
    override fun runCallbacks() {
        extensions.forEach { it.invoke(hostProvider.host!!) }
    }
}
