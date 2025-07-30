package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

/**
 * Represents a simple named kernel parameter with a string value.
 * This parameter is identified by a single alias provided via the `name` property.
 * It inherits functionality to parse and serialize command-line arguments in the format "-name=value".
 *
 * @param name The name of the parameter, which serves as its alias.
 */
class SimpleNamedKernelStringParameter(
    name: String,
) : SimpleNamedKernelParameter<String>(
        name,
        { argValue, _ -> argValue },
    )
