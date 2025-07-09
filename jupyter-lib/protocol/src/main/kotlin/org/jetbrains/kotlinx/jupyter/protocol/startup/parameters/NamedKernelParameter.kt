package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Abstract base class for named kernel parameters that follow the format "-name=value".
 * Provides a common implementation for parsing and serializing named parameters.
 *
 * @param T The type of value that this parameter represents
 * @property aliases List of alternative names for this parameter
 */
abstract class NamedKernelParameter<T : Any>(
    val aliases: List<String>,
) : KernelParameter<T> {
    /**
     * Parses a string value into a value of type T.
     * This method is called after the parameter name has been matched and the value extracted.
     *
     * @param argValue The string value to parse (without the parameter name prefix)
     * @param previousValue The previously parsed value, if any
     * @return The parsed value
     * @throws Exception if the value is invalid
     */
    abstract fun parseValue(
        argValue: String,
        previousValue: T?,
    ): T

    /**
     * Serializes a value of type T to a string representation (without the parameter name prefix).
     *
     * @param value The value to serialize
     * @return The string representation of the value, or null if the value shouldn't be included
     */
    abstract fun serializeValue(value: T): String?

    /**
     * Attempts to parse a command-line argument if it matches one of this parameter's aliases.
     * Checks if the argument starts with "-alias=" for any of the aliases.
     *
     * @param arg The command-line argument to parse
     * @param previousValue The previously parsed value, if any
     * @return The parsed value, or null if the argument doesn't match any of this parameter's aliases
     */
    override fun tryParse(
        arg: String,
        previousValue: T?,
    ): T? {
        for (alias in aliases) {
            val prefix = "-$alias="
            if (arg.startsWith(prefix)) {
                val value = arg.substringAfter(prefix)
                return parseValue(value, previousValue)
            }
        }
        return null
    }

    /**
     * Serializes a value to a command-line argument with the parameter's primary alias.
     *
     * @param value The value to serialize
     * @return The command-line argument in the format "-alias=value", or null if the value shouldn't be included
     */
    override fun serialize(value: T): String? {
        val serializedValue = serializeValue(value) ?: return null
        return "-${aliases.first()}=$serializedValue"
    }
}
