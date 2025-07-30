package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams

/**
 * Configuration data for a Jupyter kernel.
 * Combines Jupyter protocol-specific parameters with kernel-specific parameters.
 *
 * This class can be converted to command line arguments for kernel startup via [toArgs] extension function.
 */
data class KernelConfig<T : KernelOwnParams>(
    val jupyterParams: KernelJupyterParams,
    val ownParams: T,
)
