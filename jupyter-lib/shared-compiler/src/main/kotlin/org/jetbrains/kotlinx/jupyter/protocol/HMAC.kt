package org.jetbrains.kotlinx.jupyter.protocol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HMAC(algorithm: String, key: String?) {
    private val mac = if (key?.isNotBlank() == true) Mac.getInstance(algorithm) else null

    init {
        mac?.init(SecretKeySpec(key!!.toByteArray(), algorithm))
    }

    @Synchronized
    operator fun invoke(data: Iterable<ByteArray>): String? =
        mac?.let { mac ->
            data.forEach { mac.update(it) }
            mac.doFinal().toHexString()
        }

    operator fun invoke(vararg data: ByteArray): String? = invoke(data.asIterable())
}
