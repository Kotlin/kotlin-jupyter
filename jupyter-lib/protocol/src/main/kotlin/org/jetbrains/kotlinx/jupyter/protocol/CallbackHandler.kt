package org.jetbrains.kotlinx.jupyter.protocol

import org.slf4j.Logger
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

class CallbackHandler<T>(
    private val logger: Logger,
) : Closeable {
    private val callbacks = CopyOnWriteArrayList<(T) -> Unit>()

    fun addCallback(callback: (T) -> Unit) {
        callbacks.add(callback)
    }

    fun runCallbacks(payload: T) {
        callbacks.forEach { callback ->
            try {
                callback(payload)
            } catch (e: Throwable) {
                logger.error("Exception thrown while processing a callback", e)
            }
        }
    }

    override fun close() {
        callbacks.clear()
    }
}
