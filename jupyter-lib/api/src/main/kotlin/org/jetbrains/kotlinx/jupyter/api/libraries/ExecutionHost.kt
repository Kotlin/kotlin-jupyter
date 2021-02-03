package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

/**
 * Used in [ExecutionCallback] to get [KotlinKernelHost] context
 */
interface ExecutionHost {
    fun <T> execute(callback: ExecutionCallback<T>): T
}
