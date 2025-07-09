package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

import kotlin.reflect.KMutableProperty0

/**
 * Class that extends BoundKernelParameter with the ability to update the parameter value.
 * This allows for both parsing and serializing parameter values.
 *
 * @param T The type of value that this parameter represents
 * @property valueUpdater Function that updates the stored value of the parameter
 */
class MutableBoundKernelParameter<T : Any>(
    parameter: KernelParameter<T>,
    val valueUpdater: (T?) -> Unit,
    override val valueProvider: () -> T?,
) : BoundKernelParameter<T>(parameter, valueProvider) {
    /**
     * Convenience constructor that uses a mutable property for both value updating and providing.
     *
     * @param parameter The parameter handler to use
     * @param property The mutable property to bind to
     */
    constructor(
        parameter: KernelParameter<T>,
        property: KMutableProperty0<T?>,
    ) : this(parameter, property::set, property::get)

    /**
     * Attempts to parse a command-line argument and update the parameter value if successful.
     *
     * @param arg The command-line argument to parse
     * @return true if the argument was successfully parsed and the value updated, false otherwise
     */
    fun tryParse(arg: String): Boolean {
        val newValue = parameter.tryParse(arg, valueProvider()) ?: return false
        valueUpdater(newValue)
        return true
    }
}
