package org.jetbrains.kotlinx.jupyter.spring.starter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.jupyterName
import org.jetbrains.kotlinx.jupyter.messaging.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.makeRawMessage
import org.jetbrains.kotlinx.jupyter.protocol.CallbackHandler
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.protocol.data
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.runServer
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.WsKernelPorts
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

private const val CHANNEL_FIELD_NAME = "channel"

/** Can be used on both server and client side */
class WebSocketMessageHandler(
    private val logger: org.slf4j.Logger,
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

private class JupyterWebSocketServer(
    address: InetSocketAddress?,
    loggerFactory: KernelLoggerFactory,
    onMessageReceive: (JupyterSocketType, RawMessage) -> Unit,
) : WebSocketServer(address) {
    private val logger = loggerFactory.getLogger(this::class)
    private val messageHandler = WebSocketMessageHandler(logger, onMessageReceive)
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

    override fun onError(conn: WebSocket, ex: Exception?) {
        System.err.println("an error occurred on connection " + conn.remoteSocketAddress + ":" + ex)
    }

    override fun onStart() {}
}

private class JupyterWsServerSocketManagerImpl(
    loggerFactory: KernelLoggerFactory,
    config: KernelConfig,
) : JupyterWsServerSocketManagerBase(loggerFactory, config), JupyterSocketManager {
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
}


private abstract class JupyterWsServerSocketManagerBase(
    private val loggerFactory: KernelLoggerFactory,
    config: KernelConfig,
) : JupyterSocketManager, Closeable {
    private class JupyterWsSocketHolder(val socket: JupyterWsSocketQueued, val processIncomingMessages: Boolean)

    private val socketsMap = EnumMap<JupyterSocketType, JupyterWsSocketHolder>(JupyterSocketType::class.java)

    private fun fromSocketType(type: JupyterSocketType) =
        socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    protected fun createCallbackBasedSocketWrapper(type: JupyterSocketType): JupyterCallbackBasedSocket {
        return JupyterWsSocketQueued(loggerFactory, wsServer::currentWebSockets, channel = type).also {
            socketsMap[type] = JupyterWsSocketHolder(it, processIncomingMessages = true)
        }
    }

    protected fun createSendSocketWrapper(
        @Suppress("SameParameterValue") // I know it's always IOPUB, but let's keep it for consistency
        type: JupyterSocketType,
    ): JupyterSendSocket {
        return JupyterWsSocketQueued(loggerFactory, wsServer::currentWebSockets, channel = type).also {
            socketsMap[type] = JupyterWsSocketHolder(it, processIncomingMessages = false)
        }
    }

    private val wsServer: JupyterWebSocketServer = run {
        val ports = config.ports
        require(ports is WsKernelPorts) { "Wrong KernelAddress type" }
        val address = if (config.host == "*") {
            InetSocketAddress(/* port = */ ports.port) // InetSocketAddress does not support "*" as a hostname
        } else {
            InetSocketAddress(/* hostname = */ config.host, /* port = */ ports.port)
        }

        JupyterWebSocketServer(
            address = address,
            loggerFactory = loggerFactory,
            onMessageReceive = { type: JupyterSocketType, message: RawMessage ->
                fromSocketType(type).socket.messageReceived(message)
            }
        )
    }

    override fun listen() {
        val mainThread = Thread.currentThread()
        val socketListenerThreads = socketsMap.values.mapNotNull {
            it.takeIf(JupyterWsSocketHolder::processIncomingMessages)?.socket?.startListening(mainThread)
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

private interface JupyterWsSocket : JupyterCallbackBasedSocket {
    fun messageReceived(msg: RawMessage)
}

interface JupyterClientSockets : Closeable {
    val shell: JupyterSendReceiveSocket
    val control: JupyterSendReceiveSocket
    val ioPub: JupyterReceiveSocket
    val stdin: JupyterSendReceiveSocket
}

class JupyterWsClientSocketManagerImpl(
    private val loggerFactory: KernelLoggerFactory,
    createWsClient: (WebSocketMessageHandler) -> WebSocketClient,
    checkErrors: () -> List<Exception>,
) : JupyterClientSockets {
    private val logger = loggerFactory.getLogger(this::class)
    private val socketsMap = EnumMap<JupyterSocketType, JupyterWsSocketImmediate>(JupyterSocketType::class.java)

    private fun fromSocketType(type: JupyterSocketType) =
        socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    // we call `connectBlocking` only after all socket wrappers have been set up
    private val wsClient: WebSocketClient = createWsClient(
        WebSocketMessageHandler(
            logger = logger,
            onMessageReceive = { type, message ->
                fromSocketType(type).messageReceived(message)
            },
        ),
    )

    private fun createCallbackBasedSocketWrapper(type: JupyterSocketType): JupyterCallbackBasedSocket {
        return JupyterWsSocketImmediate(loggerFactory, getWebSockets = { listOf(wsClient) }, channel = type).also {
            socketsMap[type] = it
        }
    }

    override val shell: JupyterSendReceiveSocket = createCallbackBasedSocketWrapper(JupyterSocketType.SHELL)
        .sendReceive()
    override val control: JupyterSendReceiveSocket = createCallbackBasedSocketWrapper(JupyterSocketType.CONTROL)
        .sendReceive()
    override val ioPub: JupyterReceiveSocket = createCallbackBasedSocketWrapper(JupyterSocketType.IOPUB)
        .sendReceive()
    override val stdin: JupyterSendReceiveSocket = createCallbackBasedSocketWrapper(JupyterSocketType.STDIN)
        .sendReceive()

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

private abstract class JupyterWsSocketBase(
    loggerFactory: KernelLoggerFactory,
    private val getWebSockets: () -> Iterable<WebSocket>,
    private val channel: JupyterSocketType,
) : JupyterWsSocket {
    private val logger = loggerFactory.getLogger(this::class)
    protected val callbacks = CallbackHandler(logger)

    override fun sendRawMessage(msg: RawMessage) {
        val msgDataJsonString = Json.encodeToString(
            JsonObject(msg.data + (CHANNEL_FIELD_NAME to JsonPrimitive(channel.jupyterName)))
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
        val resultBuffer = ByteBuffer.allocate(
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
}

private class JupyterWsSocketImmediate(
    loggerFactory: KernelLoggerFactory,
    getWebSockets: () -> Iterable<WebSocket>,
    channel: JupyterSocketType,
) : JupyterWsSocketBase(loggerFactory, getWebSockets, channel), Closeable {
    override fun messageReceived(msg: RawMessage) {
        callbacks.runCallbacks(msg)
    }

    override fun close() {
        callbacks.clear()
    }
}

private class JupyterWsSocketQueued(
    loggerFactory: KernelLoggerFactory,
    getWebSockets: () -> Iterable<WebSocket>,
    channel: JupyterSocketType,
) : JupyterWsSocketBase(loggerFactory, getWebSockets, channel) {
    private val messages: ArrayBlockingQueue<RawMessage> = ArrayBlockingQueue(256)

    override fun messageReceived(msg: RawMessage) {
        messages.offer(msg)
    }

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
            }
        }
    }
}

fun runWebSocketServer(replSettings: DefaultReplSettings) {
    runServer(replSettings, ::JupyterWsServerSocketManagerImpl)
}
