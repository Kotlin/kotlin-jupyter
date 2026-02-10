package org.jetbrains.kotlinx.jupyter.compiler.daemon.transport

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * KrpcTransport for WebSocket client side.
 */
class WebSocketClientKrpcTransport(
    uri: URI,
) : WebSocketKrpcTransportBase() {
    private val connectLatch = CountDownLatch(1)

    override var connection: WebSocketClient =
        object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake) {
                connectLatch.countDown()
            }

            override fun onMessage(message: String) {
                enqueueMessage(message)
            }

            override fun onMessage(message: ByteBuffer) {
                enqueueMessage(message)
            }

            override fun onClose(
                code: Int,
                reason: String,
                remote: Boolean,
            ) {
            }

            override fun onError(ex: Exception) {
                ex.printStackTrace()
            }
        }

    init {
        connection.connect()
        // Wait for connection to establish
        if (!connectLatch.await(3, TimeUnit.SECONDS)) {
            throw IllegalStateException("Failed to connect to WebSocket server")
        }
    }

    override fun closeWebSocket() {
        connection.close()
    }
}
