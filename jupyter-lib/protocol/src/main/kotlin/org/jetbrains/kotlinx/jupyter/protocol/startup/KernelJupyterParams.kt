package org.jetbrains.kotlinx.jupyter.protocol.startup

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
