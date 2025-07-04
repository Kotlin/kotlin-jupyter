package org.jetbrains.kotlinx.jupyter.protocol.startup

import org.jetbrains.kotlinx.jupyter.protocol.HMAC

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
) {
    val hmac by lazy {
        HMAC(
            algorithm = signatureScheme.replace("-", ""),
            key = signatureKey,
        )
    }
}
