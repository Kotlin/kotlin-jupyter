package org.jetbrains.kotlinx.jupyter.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.messaging.makeRawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.jupyterName
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

        // offsets are:
        // * the first is for the main content start
        // * `buffersAmount` more offsets for the actual buffers
        val offsets = IntArray(buffersAmount + 1) { intBuffer.get() }

        // using the total message length as the final offset for the last offset pair in the `zipWithNext` call.
        val buffers =
            (offsets.asSequence() + message.limit())
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
    }

    private fun handleMessage(
        message: String,
        byteBuffers: List<ByteArray>,
    ) {
        val json = Json.decodeFromString<JsonElement>(message).jsonObject
        val channel =
            json[CHANNEL_FIELD_NAME]?.jsonPrimitive?.content ?: run {
                logger.warn("No channel specified.")
                return
            }

        val socketType =
            JupyterSocketType.entries.firstOrNull {
                it.jupyterName == channel
            } ?: run {
                logger.warn("Unknown channel: $channel")
                return
            }

        val rawMessage = makeRawMessage(byteBuffers, json)
        onMessageReceive(socketType, rawMessage)
    }
}
