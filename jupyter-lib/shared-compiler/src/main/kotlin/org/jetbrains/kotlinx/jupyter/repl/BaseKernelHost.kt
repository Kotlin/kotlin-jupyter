package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

interface BaseKernelHost {
    fun <T> withHost(currentHost: KotlinKernelHost, callback: () -> T): T
}
