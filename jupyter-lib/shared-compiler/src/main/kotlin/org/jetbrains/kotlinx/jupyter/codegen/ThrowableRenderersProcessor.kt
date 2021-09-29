package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderer

interface ThrowableRenderersProcessor {
    fun renderThrowable(throwable: Throwable): Any?

    fun register(renderer: ThrowableRenderer)
}
