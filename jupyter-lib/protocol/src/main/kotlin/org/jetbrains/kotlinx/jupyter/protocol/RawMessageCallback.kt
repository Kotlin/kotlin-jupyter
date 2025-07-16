package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.slf4j.Logger
import java.io.Closeable

fun interface RawMessageCallback {
    @Throws(InterruptedException::class)
    operator fun invoke(msg: RawMessage)
}

class CallbackHandler(
    private val logger: Logger,
) : Closeable {
    private val callbacks = mutableSetOf<RawMessageCallback>()

    fun addCallback(callback: RawMessageCallback) {
        callbacks.add(callback)
    }

    @Throws(InterruptedException::class)
    fun runCallbacks(payload: RawMessage) {
        callbacks.forEach { callback ->
            try {
                callback(payload)
            } catch (e: Throwable) {
                if (e is InterruptedException) {
                    throw e
                }
                logger.error("Exception thrown while processing a message", e)
            }
        }
    }

    override fun close() {
        callbacks.clear()
    }
}
