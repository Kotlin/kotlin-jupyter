package org.jetbrains.kotlinx.jupyter.api

interface ThrowableRenderersProcessor {
    fun renderThrowable(throwable: Throwable): Any?

    fun register(renderer: ThrowableRenderer)
}
