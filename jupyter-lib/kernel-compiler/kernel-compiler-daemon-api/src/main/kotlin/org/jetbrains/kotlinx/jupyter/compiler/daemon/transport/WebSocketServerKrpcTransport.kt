package org.jetbrains.kotlinx.jupyter.compiler.daemon.transport

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

class WebSocketServerKrpcTransport(
    port: Int,
) : WebSocketKrpcTransportBase() {
    private val connectionAtomic = AtomicReference<WebSocket?>(null)

    override val connection: WebSocket? get() = connectionAtomic.get()

    private val server =
        object : WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(
                conn: WebSocket,
                handshake: ClientHandshake,
            ) {
                // if we don't have a connection yet, set it
                // otherwise, ignore the new connection
                connectionAtomic.compareAndSet(null, conn)
            }

            override fun onClose(
                conn: WebSocket,
                code: Int,
                reason: String,
                remote: Boolean,
            ) {
                // if the connection is the one we're tracking, clear it
                connectionAtomic.compareAndSet(conn, null)
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
