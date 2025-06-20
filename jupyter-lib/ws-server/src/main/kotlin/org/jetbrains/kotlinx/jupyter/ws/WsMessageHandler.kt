package org.jetbrains.kotlinx.jupyter.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.jupyterName
import org.jetbrains.kotlinx.jupyter.messaging.makeRawMessage
import org.slf4j.Logger
import java.nio.ByteBuffer

/** Can be used on both server and client side */
internal class WsMessageHandler(
    private val logger: Logger,
    private val onMessageReceive: (JupyterSocketType, RawMessage) -> Unit,
) {
    fun onMessage(message: String) {
        handleMessage(message = message, byteBuffers = emptyList())
    }

    fun onMessage(message: ByteBuffer) {
        val intBuffer = message.asIntBuffer()
        val buffersAmount = intBuffer.get() - 1
        val offsets = IntArray(buffersAmount + 2)
        for (i in 0 until offsets.size - 1) {
            offsets[i] = intBuffer.get()
        }

        // Additionally, appending the offset of the contents' end, to be able to use `zipWithNext`.
        offsets[offsets.size - 1] = message.limit()
        val buffers = offsets.asSequence()
            .zipWithNext { start, end ->
                message.position(start)
                ByteArray(end - start)
                    .also {
                        message.get(it)
                    }
            }.iterator()

        // the first buffer is actually the main message content
        val message = buffers.next().decodeToString()

        // the remaining buffers in the sequence are real byteBuffers
        handleMessage(message = message, byteBuffers = buffers.asSequence().toList())

        // TODO support interrupt exception, should close gracefully
        //  Add error handling in general
    }

    private fun handleMessage(message: String, byteBuffers: List<ByteArray>) {
        val json = Json.decodeFromString<JsonElement>(message).jsonObject
        val channel = json[CHANNEL_FIELD_NAME]?.jsonPrimitive?.content ?: run {
            logger.warn("No channel specified.")
            return
        }

        val socketType = JupyterSocketType.entries.firstOrNull {
            it.jupyterName == channel
        } ?: run {
            logger.warn("Unknown channel: $channel")
            return
        }

        val rawMessage = makeRawMessage(json, byteBuffers)
        onMessageReceive(socketType, rawMessage)
    }
}
