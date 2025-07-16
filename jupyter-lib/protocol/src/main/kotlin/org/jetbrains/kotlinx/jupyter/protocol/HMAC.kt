package org.jetbrains.kotlinx.jupyter.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

class HMAC(
    algorithm: String,
    key: String,
) {
    private val mac: Mac =
        Mac.getInstance(algorithm).apply {
            init(SecretKeySpec(key.toByteArray(), algorithm))
        }

    @Synchronized
    operator fun invoke(data: Iterable<ByteArray>): String {
        data.forEach { mac.update(it) }
        return mac.doFinal().toHexString()
    }

    operator fun invoke(vararg data: ByteArray): String = invoke(data.asIterable())
}
