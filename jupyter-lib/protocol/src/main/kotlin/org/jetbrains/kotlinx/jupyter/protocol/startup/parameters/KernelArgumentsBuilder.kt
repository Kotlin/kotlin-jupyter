package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

import java.io.File

/**
 * Builder class for constructing kernel arguments from command-line parameters.
 *
 * This class handles both parsing of command-line arguments into a structured format
 * and serializing the current state back into command-line arguments.
 * It integrates kernel-specific parameters with common parameters like the config file location.
 *
 * @param T The type of kernel-specific parameters this builder handles
 * @property ownParamsBuilder Builder for kernel-specific parameters
 * @property cfgFile Optional configuration file reference
 */
class KernelArgumentsBuilder<T : KernelOwnParams>(
    private val ownParamsBuilder: KernelOwnParamsBuilder<T>,
    private var cfgFile: File? = null,
) {
    /**
     * List of bound parameters that connect parameter definitions with their property references.
     * This allows for automatic parsing and serialization of command-line arguments.
     */
    private val boundParameters =
        buildList {
            addAll(ownParamsBuilder.boundParameters)
            add(MutableBoundKernelParameter(configFileParameter, ::cfgFile))
        }

    /**
     * Parses command-line arguments and updates the builder's properties accordingly.
     * Each argument is processed by the appropriate parameter handler.
     *
     * @param args Array of command-line arguments to parse
     * @return Resulting [KernelArgs]
     */
    fun parseArgs(args: Array<out String>): KernelArgs<T> {
        parseKernelParameters(args, boundParameters)

        val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
        if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

        val ownParams = ownParamsBuilder.build()

        return KernelArgs(
            cfgFile = cfgFileValue,
            ownParams = ownParams,
        )
    }

    /**
     * Converts the current state of the builder into a list of command-line arguments.
     * Only non-null properties are included in the result.
     *
     * @return List of command-line arguments representing the current state
     */
    fun argsList(): List<String> = serializeKernelParameters(boundParameters)
}
