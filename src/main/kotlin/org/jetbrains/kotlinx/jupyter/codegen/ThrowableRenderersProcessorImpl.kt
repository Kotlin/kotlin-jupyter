package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderer

class ThrowableRenderersProcessorImpl : ThrowableRenderersProcessor {
    private val renderers = mutableListOf<ThrowableRenderer>()

    override fun renderThrowable(throwable: Throwable): Any? {
        return renderers.firstOrNull { it.accepts(throwable) }?.render(throwable)
    }

    override fun register(renderer: ThrowableRenderer) {
        renderers.add(renderer)
    }
}
