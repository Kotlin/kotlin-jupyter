package org.jetbrains.kotlinx.jupyter.protocol.startup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelArgs
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelOwnParams
import java.io.File

const val KERNEL_TRANSPORT_SCHEME = "tcp"
const val KERNEL_SIGNATURE_SCHEME = "HmacSHA256"

const val ANY_HOST_NAME = "*"
const val LOCALHOST = "localhost"

data class KernelJupyterParams(
    val signatureScheme: String,
    val signatureKey: String,
    val host: String,
    val ports: KernelPorts,
    val transport: String?,
)

fun <T : KernelOwnParams> KernelArgs<T>.getConfig(jupyterParamsSerializer: KSerializer<KernelJupyterParams>): KernelConfig<T> =
    KernelConfig(
        jupyterParams = cfgFile.toKernelJupyterParams(jupyterParamsSerializer),
        ownParams = ownParams,
    )

fun File.toKernelJupyterParams(serializer: KSerializer<KernelJupyterParams>): KernelJupyterParams {
    val jsonString = canonicalFile.readText()
    return Json.decodeFromString(serializer, jsonString)
}
