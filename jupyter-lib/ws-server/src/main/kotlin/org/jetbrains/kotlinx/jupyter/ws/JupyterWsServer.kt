package org.jetbrains.kotlinx.jupyter.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections
import java.util.EnumMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class WsKernelPorts(
    val port: Int,
) : KernelPorts {
    override fun serialize(): JsonObject =
        buildJsonObject {
            put("ws_port", JsonPrimitive(port))
        }

    override fun toString(): String = "WsKernelPorts(${serialize()})"
}

class JupyterWsServerRunner : JupyterServerRunner {
    override fun tryDeserializePorts(json: JsonObject): KernelPorts? {
        return WsKernelPorts(port = Json.decodeFromJsonElement<Int>(json["ws_port"] ?: return null))
    }

    override fun canRun(ports: KernelPorts): Boolean = ports is WsKernelPorts

    override fun run(
        jupyterParams: KernelJupyterParams,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Iterable<Closeable>,
    ) {
        val socketsMap = EnumMap<JupyterSocketType, JupyterWsSocketHolder>(JupyterSocketType::class.java)

        val wsServer: JupyterWsServer =
            run {
                val ports = jupyterParams.ports
                require(ports is WsKernelPorts) { "Wrong KernelPorts type: $ports" }
                val address =
                    if (jupyterParams.host == ANY_HOST_NAME) {
                        // InetSocketAddress does not support "*" as a hostname
                        InetSocketAddress(/* port = */ ports.port)
                    } else {
                        InetSocketAddress(/* hostname = */ jupyterParams.host, /* port = */ ports.port)
                    }

                JupyterWsServer(
                    address = address,
                    loggerFactory = loggerFactory,
                    onMessageReceive = { type: JupyterSocketType, message: RawMessage ->
                        socketsMap[type]?.socket?.messageReceived(message)
                            ?: throw RuntimeException("Unknown socket type: $type")
                    },
                ).apply { isReuseAddr = true }
            }

        fun createCallbackBasedSocketWrapper(type: JupyterSocketType): JupyterCallbackBasedSocket =
            WsCallbackBasedSocketQueued(loggerFactory, wsServer::currentWebSockets, channel = type).also {
                socketsMap[type] = JupyterWsSocketHolder(it, processIncomingMessages = true)
            }

        fun createSendSocketWrapper(type: JupyterSocketType): JupyterSendSocket =
            WsCallbackBasedSocketQueued(loggerFactory, wsServer::currentWebSockets, channel = type).also {
                socketsMap[type] = JupyterWsSocketHolder(it, processIncomingMessages = false)
            }

        val sockets =
            object : JupyterServerImplSockets {
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

        val closeables = setup(sockets)

        tryFinally(
            action = {
                val mainThread = Thread.currentThread()
                val socketListenerThreads =
                    socketsMap.values.mapNotNull {
                        it
                            .takeIf(JupyterWsSocketHolder::processIncomingMessages)
                            ?.socket
                            ?.startListening(mainListenerThread = mainThread)
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
            },
            finally = {
                mergeExceptions {
                    for (socket in socketsMap.values) {
                        catchIndependently { socket.socket.close() }
                    }
                    for (closeable in closeables) {
                        catchIndependently { closeable.close() }
                    }
                    catchIndependently { wsServer.stop() }
                }
            },
        )
    }

    private class JupyterWsSocketHolder(
        val socket: WsCallbackBasedSocketQueued,
        val processIncomingMessages: Boolean,
    )
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

    /**
     * Starts a new thread listening for incoming messages. Returns this new thread.
     * Does not [close] this instance when the message processing is over.
     */
    fun startListening(mainListenerThread: Thread): Thread =
        thread(name = "WsCallbackBasedSocketQueued($channel)") {
            try {
                while (true) {
                    val message = messages.take()
                    callbacks.runCallbacks(message)
                }
            } catch (_: InterruptedException) {
                mainListenerThread.interrupt()
            } catch (e: Throwable) {
                logger.error("Error during message processing", e)
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
    private val _currentWebSockets = Collections.newSetFromMap<WebSocket>(ConcurrentHashMap())

    val currentWebSockets: Iterable<WebSocket> get() = _currentWebSockets

    override fun onOpen(
        conn: WebSocket,
        handshake: ClientHandshake,
    ) {
        _currentWebSockets.add(conn)
    }

    override fun onClose(
        conn: WebSocket,
        code: Int,
        reason: String?,
        remote: Boolean,
    ) {
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

    override fun onError(
        conn: WebSocket?,
        ex: Exception?,
    ) {
        logger.error("Error in WebSocket connection", ex)
    }

    override fun onStart() {}
}
