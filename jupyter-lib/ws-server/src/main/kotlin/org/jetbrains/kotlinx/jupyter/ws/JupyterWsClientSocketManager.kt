package org.jetbrains.kotlinx.jupyter.ws

import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.LOCALHOST
import java.io.Closeable
import java.net.URI
import java.nio.ByteBuffer
import java.util.EnumMap

class JupyterWsClientSocketManager(
    private val loggerFactory: KernelLoggerFactory,
) : JupyterClientSocketManager {
    override fun open(configParams: KernelJupyterParams): JupyterClientSockets {
        val errors = mutableListOf<Exception>()
        return WsClientSockets(
            loggerFactory = loggerFactory,
            createWsClient = { messageHandler ->
                object : WebSocketClient(
                    URI(
                        /* scheme = */ "ws",
                        /* userInfo = */ null,
                        /* host = */ configParams.host.takeUnless { it == ANY_HOST_NAME } ?: LOCALHOST,
                        /* port = */ (configParams.ports as WsKernelPorts).port,
                        /* path = */ null,
                        /* query = */ null,
                        /* fragment = */ null,
                    ),
                ) {
                    override fun onMessage(message: String) = messageHandler.onMessage(message)

                    override fun onMessage(bytes: ByteBuffer) = messageHandler.onMessage(bytes)

                    override fun onOpen(handshakedata: ServerHandshake) {}

                    override fun onClose(
                        code: Int,
                        reason: String,
                        remote: Boolean,
                    ) {}

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

    private fun fromSocketType(type: JupyterSocketType) = socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    // we call `connectBlocking` only after all socket wrappers have been set up
    private val wsClient: WebSocketClient =
        createWsClient(
            WsMessageHandler(
                logger = logger,
                onMessageReceive = { type, message ->
                    fromSocketType(type).messageReceived(message)
                },
            ),
        )

    private fun createCallbackBasedSocketWrapper(type: JupyterSocketType): JupyterCallbackBasedSocket =
        WsCallbackBasedSocketImmediate(loggerFactory, getWebSockets = { listOf(wsClient) }, channel = type).also {
            socketsMap[type] = it
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
        mergeExceptions {
            for (socket in socketsMap.values) {
                catchIndependently { socket.close() }
            }
            catchIndependently { wsClient.closeBlocking() }
        }
    }
}

private class WsCallbackBasedSocketImmediate(
    loggerFactory: KernelLoggerFactory,
    getWebSockets: () -> Iterable<WebSocket>,
    channel: JupyterSocketType,
) : WsCallbackBasedSocket(loggerFactory, getWebSockets, channel),
    Closeable {
    override fun messageReceived(msg: RawMessage) {
        callbacks.runCallbacks(msg)
    }
}
