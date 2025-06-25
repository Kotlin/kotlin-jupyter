package org.jetbrains.kotlinx.jupyter.ws

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.WsKernelPorts
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.EnumMap
import java.util.concurrent.ArrayBlockingQueue
import kotlin.collections.set
import kotlin.concurrent.thread

class JupyterWsServerSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    config: KernelConfig,
) : JupyterSocketManager, Closeable {
    private class JupyterWsSocketHolder(val socket: WsCallbackBasedSocketQueued, val processIncomingMessages: Boolean)

    private val socketsMap = EnumMap<JupyterSocketType, JupyterWsSocketHolder>(JupyterSocketType::class.java)

    private val wsServer: JupyterWsServer = run {
        val ports = config.ports
        require(ports is WsKernelPorts) { "Wrong KernelAddress type" }
        val address = if (config.host == "*") {
            InetSocketAddress(/* port = */ ports.port) // InetSocketAddress does not support "*" as a hostname
        } else {
            InetSocketAddress(/* hostname = */ config.host, /* port = */ ports.port)
        }

        JupyterWsServer(
            address = address,
            loggerFactory = loggerFactory,
            onMessageReceive = { type: JupyterSocketType, message: RawMessage ->
                socketsMap[type]?.socket?.messageReceived(message)
                    ?: throw RuntimeException("Unknown socket type: $type")
            },
        )
    }

    private fun createCallbackBasedSocketWrapper(type: JupyterSocketType): JupyterCallbackBasedSocket {
        return WsCallbackBasedSocketQueued(loggerFactory, wsServer::currentWebSockets, channel = type).also {
            socketsMap[type] = JupyterWsSocketHolder(it, processIncomingMessages = true)
        }
    }

    private fun createSendSocketWrapper(
        @Suppress("SameParameterValue") // I know it's always IOPUB, but let's keep it for consistency
        type: JupyterSocketType,
    ): JupyterSendSocket = WsCallbackBasedSocketQueued(loggerFactory, wsServer::currentWebSockets, channel = type).also {
        socketsMap[type] = JupyterWsSocketHolder(it, processIncomingMessages = false)
    }

    override val sockets = object : JupyterServerImplSockets {
        override val shell = createCallbackBasedSocketWrapper(JupyterSocketType.SHELL)
        override val control = createCallbackBasedSocketWrapper(JupyterSocketType.CONTROL)
        override val iopub = createSendSocketWrapper(JupyterSocketType.IOPUB)
        override val stdin = createCallbackBasedSocketWrapper(JupyterSocketType.STDIN).sendReceive()

        init {
            // creating and registering heartbeat
            createCallbackBasedSocketWrapper(JupyterSocketType.HB)
                .also { socket -> socket.onRawMessage(socket::sendRawMessage) }
        }
    }


    override fun listen() {
        val mainThread = Thread.currentThread()
        val socketListenerThreads = socketsMap.values.mapNotNull {
            it.takeIf(JupyterWsSocketHolder::processIncomingMessages)
                ?.socket?.startListening(mainListenerThread = mainThread)
        }
        wsServer.run()
        try {
            socketListenerThreads.forEach {
                it.join()
            }
        } catch (_: InterruptedException) {
            socketListenerThreads.forEach {
                it.interrupt()
            }
        }
    }

    override fun close() {
        wsServer.stop() // stopping closes all WebSocket connections, which in turn removes them from currentWebSockets
    }
}

private class WsCallbackBasedSocketQueued(
    loggerFactory: KernelLoggerFactory,
    getWebSockets: () -> Iterable<WebSocket>,
    channel: JupyterSocketType,
) : WsCallbackBasedSocket(loggerFactory, getWebSockets, channel) {
    private val logger = loggerFactory.getLogger(this::class)
    private val messages: ArrayBlockingQueue<RawMessage> = ArrayBlockingQueue(256)

    override fun messageReceived(msg: RawMessage) {
        messages.offer(msg)
    }

    /** Starts a new thread listening for incoming messages. Returns this new thread. */
    fun startListening(mainListenerThread: Thread): Thread {
        return thread {
            try {
                while (true) {
                    val message = messages.take()
                    callbacks.runCallbacks(message)
                }
            } catch (_: InterruptedException) {
                callbacks.clear()
                mainListenerThread.interrupt()
            } catch (e: Throwable) {
                logger.error("Error during message processing", e)
            }
        }
    }
}

private class JupyterWsServer(
    address: InetSocketAddress?,
    loggerFactory: KernelLoggerFactory,
    onMessageReceive: (JupyterSocketType, RawMessage) -> Unit,
) : WebSocketServer(address) {
    private val logger = loggerFactory.getLogger(this::class)
    private val messageHandler = WsMessageHandler(logger, onMessageReceive)
    private val _currentWebSockets = mutableSetOf<WebSocket>()

    val currentWebSockets: Iterable<WebSocket> get() = _currentWebSockets

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        _currentWebSockets.add(conn)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        _currentWebSockets.remove(conn)
    }

    override fun onMessage(
        conn: WebSocket, // we don't care, we'll broadcast any possible responses to everybody
        message: String,
    ): Unit = messageHandler.onMessage(message)

    override fun onMessage(
        conn: WebSocket, // we don't care, we'll broadcast any possible responses to everybody
        message: ByteBuffer,
    ): Unit = messageHandler.onMessage(message)

    override fun onError(conn: WebSocket?, ex: Exception?) {
        logger.error("Error in WebSocket connection", ex)
    }

    override fun onStart() {}
}
