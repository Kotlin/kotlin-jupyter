package org.jetbrains.kotlinx.jupyter.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.java_websocket.WebSocket
import org.jetbrains.kotlinx.jupyter.protocol.CallbackHandler
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.api.jupyterName
import org.jetbrains.kotlinx.jupyter.protocol.data
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val CHANNEL_FIELD_NAME = "channel"

/**
 * Does not own the WebSockets it gets with [getWebSockets].
 * Calling [close] on this instance is still required, but won't close any WebSockets.
 */
internal abstract class WsCallbackBasedSocket(
    loggerFactory: KernelLoggerFactory,
    private val getWebSockets: () -> Iterable<WebSocket>,
    protected val channel: JupyterSocketType,
) : JupyterCallbackBasedSocket,
    Closeable {
    private val logger = loggerFactory.getLogger(this::class)
    protected val callbacks = CallbackHandler(logger)

    abstract fun messageReceived(msg: RawMessage)

    override fun sendRawMessage(msg: RawMessage) {
        val msgDataJsonString =
            Json.encodeToString(
                JsonObject(msg.data + (CHANNEL_FIELD_NAME to JsonPrimitive(channel.jupyterName))),
            )
        for (webSocket in getWebSockets()) {
            if (msg.id.isEmpty()) {
                webSocket.send(msgDataJsonString)
            } else {
                webSocket.send(messageWithBuffersToBytes(msgDataJsonString = msgDataJsonString, buffers = msg.id))
            }
        }
    }

    /**
     * See `deserialize_binary_message` in the [Jupyter server implementation](https://github.com/jupyter-server/jupyter_server/blob/main/jupyter_server/services/kernels/connection/base.py#L54-L61).
     *
     * Header:
     * - 4 bytes: number of msg parts (nbufs) as 32b int
     * - 4 * nbufs bytes: offset for each buffer as integer as 32b int
     *
     * Offsets are from the start of the buffer, including the header.
     * Keep in mind that JSON document is included in the buffer count.
     * All numbers are in big-endian format.
     */
    private fun messageWithBuffersToBytes(
        msgDataJsonString: String,
        buffers: List<ByteArray>,
    ): ByteBuffer? {
        val msgDataStartIndex = 4 * (2 + buffers.size)
        val msgDataJsonBytes = msgDataJsonString.toByteArray(Charsets.UTF_8)
        val resultBuffer =
            ByteBuffer.allocate(
                /* capacity = */ msgDataStartIndex + msgDataJsonBytes.size + buffers.sumOf { it.size },
            )
        resultBuffer.order(ByteOrder.BIG_ENDIAN)
        resultBuffer.putInt(buffers.size + 1)
        run {
            var offset = msgDataStartIndex
            resultBuffer.putInt(offset)
            offset += msgDataJsonBytes.size
            for (buffer in buffers) {
                resultBuffer.putInt(offset)
                offset += buffer.size
            }
        }
        resultBuffer.put(msgDataJsonBytes)
        for (buffer in buffers) {
            resultBuffer.put(buffer)
        }
        resultBuffer.rewind()
        return resultBuffer
    }

    override fun onRawMessage(callback: RawMessageCallback) = callbacks.addCallback(callback)

    override fun close() {
        callbacks.close()
    }
}
