package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

/**
 * Used in [Execution.execute] to get [KotlinKernelHost] context
 */
interface ExecutionHost {

    fun <T> execute(callback: KotlinKernelHost.() -> T): T

    fun <T> execute(execution: Execution<T>) = execution.execute(this)
}
