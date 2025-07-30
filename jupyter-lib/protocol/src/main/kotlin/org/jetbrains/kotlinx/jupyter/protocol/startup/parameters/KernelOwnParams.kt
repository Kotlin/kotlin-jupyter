package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Marker interface for kernel-specific parameters.
 * There are also unspecific parameters defined by Jupyter protocol,
 * [org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams]
 */
interface KernelOwnParams {
    fun createBuilder(): KernelOwnParamsBuilder<*>
}
