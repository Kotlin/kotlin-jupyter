package org.jetbrains.kotlinx.jupyter.compiler.daemon.transport

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class WebSocketServerKrpcTransport(
    port: Int,
) : WebSocketKrpcTransportBase() {
    override var connection: WebSocket? = null

    private val server =
        object : WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(
                conn: WebSocket,
                handshake: ClientHandshake,
            ) {
                connection = conn
            }

            override fun onClose(
                conn: WebSocket,
                code: Int,
                reason: String,
                remote: Boolean,
            ) {
                if (connection == conn) connection = null
            }

            override fun onMessage(
                conn: WebSocket,
                message: String,
            ) {
                enqueueMessage(message)
            }

            override fun onMessage(
                conn: WebSocket,
                message: ByteBuffer,
            ) {
                enqueueMessage(message)
            }

            override fun onError(
                conn: WebSocket?,
                ex: Exception,
            ) {
                ex.printStackTrace()
            }

            override fun onStart() {
            }
        }

    init {
        server.start()
    }

    override fun closeWebSocket() {
        server.stop() // closes all open connections
    }
}
