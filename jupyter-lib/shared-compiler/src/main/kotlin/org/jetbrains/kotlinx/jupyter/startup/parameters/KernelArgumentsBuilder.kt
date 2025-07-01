package org.jetbrains.kotlinx.jupyter.startup.parameters

import org.jetbrains.kotlinx.jupyter.startup.KernelArgs
import java.io.File

class KernelArgumentsBuilder(
    private val ownParamsBuilder: KernelOwnParamsBuilder = KernelOwnParamsBuilder(),
    private var cfgFile: File? = null,
) {
    constructor(kernelArgs: KernelArgs) : this(
        KernelOwnParamsBuilder(kernelArgs.ownParams),
        kernelArgs.cfgFile,
    )

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
    fun parseArgs(args: Array<out String>): KernelArgs {
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
