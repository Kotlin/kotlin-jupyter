package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Class that binds a parameter handler to a value provider function.
 * This allows for serializing parameter values without directly accessing the storage mechanism.
 *
 * @param T The type of value that this parameter represents
 * @property parameter The parameter handler to use for serialization
 * @property valueProvider Function that provides the current value of the parameter
 */
open class BoundKernelParameter<T : Any>(
    val parameter: KernelParameter<T>,
    open val valueProvider: () -> T?,
) {
    /**
     * Serializes the current parameter value and adds it to the argument list.
     * If the value is null or the parameter handler returns null, nothing is added.
     *
     * @param argsBuilder The mutable list to add the serialized argument to
     */
    fun serialize(argsBuilder: MutableList<String>) {
        val value = valueProvider() ?: return
        val argValue = parameter.serialize(value) ?: return
        argsBuilder.add(argValue)
    }
}
