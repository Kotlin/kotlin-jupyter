package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Parses an array of command-line arguments using the provided parameter handlers.
 * For each argument, tries each parameter handler in order until one successfully parses it.
 *
 * @param args Array of command-line arguments to parse
 * @param parameters List of parameter handlers to use for parsing
 */
fun parseKernelParameters(
    args: Array<out String>,
    parameters: List<MutableBoundKernelParameter<*>>,
) {
    argsLoop@for (arg in args) {
        for (parameter in parameters) {
            if (parameter.tryParse(arg)) continue@argsLoop
        }
        throw IllegalArgumentException("Unrecognized argument: $arg")
    }
}

/**
 * Serializes kernel parameters into a list of command-line arguments.
 * Each parameter is serialized only if it has a non-null value.
 *
 * @param parameters List of parameter handlers to use for serialization
 * @return List of command-line arguments representing the parameters' values
 */
fun serializeKernelParameters(parameters: List<BoundKernelParameter<*>>): List<String> =
    buildList {
        for (parameter in parameters) {
            parameter.serialize(this)
        }
    }
