package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * A simple implementation of a named kernel parameter that handles integer values.
 *
 * @constructor Creates an instance of [SimpleNamedKernelIntParameter] and initializes it with
 * a name and a parsing logic that converts a string argument to an integer.
 *
 * @param name The name of the parameter, used for identifying it in command-line arguments.
 */
class SimpleNamedKernelIntParameter(
    name: String,
) : SimpleNamedKernelParameter<Int>(
        name,
        { arg, _ ->
            arg.toIntOrNull() ?: throw IllegalArgumentException("Argument should be integer: $arg")
        },
    )
