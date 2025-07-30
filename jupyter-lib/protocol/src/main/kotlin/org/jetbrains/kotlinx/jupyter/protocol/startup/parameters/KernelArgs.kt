package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import java.io.File

/**
 * Parsed kernel process arguments.
 * They might be converted to command line ([argsList]) or to [KernelConfig] (see [toArgs]).
 */
data class KernelArgs<T : KernelOwnParams>(
    val cfgFile: File,
    val ownParams: T,
) {
    fun argsList(): List<String> =
        KernelArgumentsBuilder(
            ownParamsBuilder = ownParams.createBuilder(),
            cfgFile = cfgFile,
        ).argsList()
}

fun <T : KernelOwnParams> KernelConfig<T>.toArgs(
    kernelJupyterParamsSerializer: KSerializer<KernelJupyterParams>,
    configFileSuffix: String = "",
): KernelArgs<T> {
    val cfgFile = File.createTempFile("kotlin-kernel-config-$configFileSuffix", ".json")
    cfgFile.deleteOnExit()
    val format = Json { prettyPrint = true }
    cfgFile.writeText(format.encodeToString(kernelJupyterParamsSerializer, jupyterParams))

    return KernelArgs(
        cfgFile = cfgFile,
        ownParams = ownParams,
    )
}
