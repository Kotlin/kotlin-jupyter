package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Builder interface for kernel-specific parameters that manages a list of bound parameters
 * and constructs a new instance of kernel-specific parameters.
 *
 * This interface is responsible for:
 * - Maintaining a list of parameters that can be bound to values
 * - Building the final kernel-specific parameters object
 *
 * @param T The type of kernel-specific parameters this builder creates
 * @property boundParameters List of mutable bound kernel parameters that can be updated and serialized
 */
interface KernelOwnParamsBuilder<T : KernelOwnParams> {
    val boundParameters: List<MutableBoundKernelParameter<out Any>>

    fun build(): T
}
