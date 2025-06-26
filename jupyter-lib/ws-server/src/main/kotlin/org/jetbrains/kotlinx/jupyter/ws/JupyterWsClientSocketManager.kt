package org.jetbrains.kotlinx.jupyter.ws

import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.Closeable
import java.net.URI
import java.nio.ByteBuffer
import java.util.EnumMap

class JupyterWsClientSocketManager(
    private val loggerFactory: KernelLoggerFactory,
) : JupyterClientSocketManager {
    override fun open(config: KernelConfig): JupyterClientSockets {
        val errors = mutableListOf<Exception>()
        return WsClientSockets(
            loggerFactory = loggerFactory,
            createWsClient = { messageHandler ->
                object : WebSocketClient(
                    URI(
                        /* scheme = */ "ws",
                        /* userInfo = */ null,
                        /* host = */ config.host.takeUnless { it == "*" } ?: "0.0.0.0",
                        /* port = */ (config.ports as WsKernelPorts).port,
                        /* path = */ null,
                        /* query = */ null,
                        /* fragment = */ null,
                    ),
                ) {
                    override fun onMessage(message: String) = messageHandler.onMessage(message)
                    override fun onMessage(bytes: ByteBuffer) = messageHandler.onMessage(bytes)

                    override fun onOpen(handshakedata: ServerHandshake) {}

                    override fun onClose(code: Int, reason: String, remote: Boolean) {}

                    override fun onError(ex: Exception) {
                        errors.add(ex)
                    }
                }
            },
            checkErrors = { errors },
        )
    }
}

private class WsClientSockets(
    private val loggerFactory: KernelLoggerFactory,
    createWsClient: (WsMessageHandler) -> WebSocketClient,
    checkErrors: () -> List<Exception>,
) : JupyterClientSockets {
    private val logger = loggerFactory.getLogger(this::class)
    private val socketsMap = EnumMap<JupyterSocketType, WsCallbackBasedSocketImmediate>(JupyterSocketType::class.java)

    private fun fromSocketType(type: JupyterSocketType) =
        socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    // we call `connectBlocking` only after all socket wrappers have been set up
    private val wsClient: WebSocketClient = createWsClient(
        WsMessageHandler(
            logger = logger,
            onMessageReceive = { type, message ->
                fromSocketType(type).messageReceived(message)
            },
        ),
    )

    private fun createCallbackBasedSocketWrapper(type: JupyterSocketType): JupyterCallbackBasedSocket {
        return WsCallbackBasedSocketImmediate(loggerFactory, getWebSockets = { listOf(wsClient) }, channel = type).also {
            socketsMap[type] = it
        }
    }

    override val shell = createCallbackBasedSocketWrapper(JupyterSocketType.SHELL)
    override val control = createCallbackBasedSocketWrapper(JupyterSocketType.CONTROL)
    override val ioPub = createCallbackBasedSocketWrapper(JupyterSocketType.IOPUB)
    override val stdin = createCallbackBasedSocketWrapper(JupyterSocketType.STDIN)

    init {
        wsClient.connectBlocking()
        val errors = checkErrors()
        if (errors.isNotEmpty()) {
            val thrownError = errors.first()
            for (error in errors.drop(1)) {
                thrownError.addSuppressed(error)
            }
            throw thrownError
        }
    }

    override fun close() {
        for (socket in socketsMap.values) {
            socket.close()
        }
        wsClient.closeBlocking()
    }
}

private class WsCallbackBasedSocketImmediate(
    loggerFactory: KernelLoggerFactory,
    getWebSockets: () -> Iterable<WebSocket>,
    channel: JupyterSocketType,
) : WsCallbackBasedSocket(loggerFactory, getWebSockets, channel), Closeable {
    override fun messageReceived(msg: RawMessage) {
        callbacks.runCallbacks(msg)
    }

    override fun close() {
        callbacks.clear()
    }
}
