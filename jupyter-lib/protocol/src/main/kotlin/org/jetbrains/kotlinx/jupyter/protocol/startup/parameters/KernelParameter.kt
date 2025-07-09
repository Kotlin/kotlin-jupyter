package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Interface for handling the parsing and serialization of a specific kernel parameter type.
 *
 * @param T The type of value that this parameter represents
 */
interface KernelParameter<T> {
    /**
     * Attempts to parse a command-line argument into a value of type T.
     *
     * @param arg The command-line argument to parse
     * @param previousValue The previously parsed value, if any
     * @return The parsed value, or null if the argument couldn't be parsed by this handler
     * @throws Exception if the argument is recognized but invalid
     */
    fun tryParse(
        arg: String,
        previousValue: T?,
    ): T?

    /**
     * Serializes a value of type T to a string representation for command-line usage.
     *
     * @param value The value to serialize
     * @return The string representation of the value, or null if the value shouldn't be included
     */
    fun serialize(value: T): String?
}
