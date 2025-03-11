package org.jetbrains.kotlinx.jupyter.spring.starter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.protocol.*
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.startServer
import org.jetbrains.kotlinx.jupyter.startup.KernelAddress
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture

private const val headerFieldName = "header"
private const val channelFieldName = "channel"
private const val parentHeaderFieldName = "parent_header"
private const val contentFieldName = "content"
private const val metadataFieldName = "metadata"

class JupyterWebSocketServer(
    address: InetSocketAddress?,
    private val loggerFactory: KernelLoggerFactory,
    private val onConnectionOpen: (webSocket: WebSocket) -> Unit,
) : WebSocketServer(address) {
    private val logger = loggerFactory.getLogger(this::class)
    private val socketManagers = mutableMapOf<WebSocket, JupyterWsSocketManager>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        socketManagers[conn] = JupyterWsSocketManagerImpl(conn, loggerFactory)
        onConnectionOpen(conn)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        println("closed " + conn.remoteSocketAddress + " with exit code " + code + " additional info: " + reason)
        socketManagers.remove(conn)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        handleMessage(conn, message = message, byteBuffers = emptyList())
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        val intBuffer = message.asIntBuffer()
        val buffersAmount = intBuffer.get()
        val offsets = IntArray(buffersAmount + 1)
        for (i in 0 until offsets.size - 1) {
            offsets[i] = intBuffer.get()
        }

        // Additionally, appending the offset of the contents' end, to be able to use `zipWithNext`.
        offsets[offsets.size - 1] = message.limit()
        val buffers = offsets.asSequence()
            .zipWithNext { start, end ->
                ByteArray(end - start)
                    .also { message.get(start, it) }
            }

        // the first buffer is actually the main message content
        val message = buffers.first().decodeToString()

        // the remaining buffers in the sequence are real byteBuffers
        handleMessage(conn, message = message, byteBuffers = buffers.toList())

        // TODO support interrupt exception, should close gracefully
        //  Add error handling in general
    }

    private fun handleMessage(conn: WebSocket, message: String, byteBuffers: List<ByteArray>) {
        val json = Json.decodeFromString<JsonElement>(message).jsonObject

        val channel = json[channelFieldName]?.jsonPrimitive?.content ?: return

        val rawMessage = RawMessageImpl(
            id = byteBuffers,
            header = json[headerFieldName]?.jsonObject ?: Json.EMPTY,
            parentHeader = json[parentHeaderFieldName]?.jsonObject,
            metadata = json[metadataFieldName]?.jsonObject,
            content = json[contentFieldName]?.jsonObject ?: Json.EMPTY,
        )

        val socketType = try {
            JupyterSocketType.valueOf(channel)
        } catch (_: IllegalArgumentException) {
            logger.warn("Unknown channel: $channel")
            return
        }

        socketManagers.getValue(conn)
            .fromSocketType(socketType)
            .sendRawMessage(rawMessage)
    }

    override fun onError(conn: WebSocket, ex: Exception?) {
        System.err.println("an error occurred on connection " + conn.remoteSocketAddress + ":" + ex)
    }

    override fun onStart() {
        println("server started successfully")
    }
}


class JupyterWsConnectionImpl(
    socket: WebSocket,
    loggerFactory: KernelLoggerFactory,
) : AbstractJupyterConnection(), JupyterWsConnectionInternal {
    override val socketManager: JupyterWsSocketManager =
        JupyterWsSocketManagerImpl(socket, loggerFactory)

    override fun close() {
        socketManager.close()
    }
}


class JupyterWsSocketManagerImpl(
    private val socket: WebSocket,
    loggerFactory: KernelLoggerFactory,
) : JupyterWsSocketManager {
    override val heartbeat: CallbackEnabledJupyterSocket = WsSocketWrapper(socket, loggerFactory)
    override val shell: CallbackEnabledJupyterSocket = WsSocketWrapper(socket, loggerFactory)
    override val control: CallbackEnabledJupyterSocket = WsSocketWrapper(socket, loggerFactory)
    override val stdin: CallbackEnabledJupyterSocket = WsSocketWrapper(socket, loggerFactory)
    override val iopub: CallbackEnabledJupyterSocket = WsSocketWrapper(socket, loggerFactory)

    private val socketsMap = buildMap {
        put(JupyterSocketType.HB, heartbeat)
        put(JupyterSocketType.SHELL, shell)
        put(JupyterSocketType.CONTROL, control)
        put(JupyterSocketType.STDIN, stdin)
        put(JupyterSocketType.IOPUB, iopub)
    }

    override fun fromSocketType(type: JupyterSocketType): CallbackEnabledJupyterSocket =
        socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    override fun close() {
        socket.close()
    }
}

interface JupyterWsConnectionInternal : JupyterConnection {
    val socketManager: JupyterWsSocketManager
}

private class WsSocketWrapper(
    val socket: WebSocket,
    loggerFactory: KernelLoggerFactory,
) : CallbackEnabledJupyterSocket {
    private val logger = loggerFactory.getLogger(this::class)

    override val callbackHandler: CallbackHandler = CallbackHandlerImpl(logger)

    private val blockingQueue = ArrayBlockingQueue<RawMessage>(1)

    override fun sendRawMessage(msg: RawMessage) {
        socket.send(offset1)
        // TODO
        //  https://jupyter-server.readthedocs.io/en/latest/developers/websocket-protocols.html
        //  take a look at the implemetation in IntelliJ

        // MessageFormat.encodeToString(it) }?.toByteArray()
        // socket.send(msg.toMessage())
    }

    override fun receiveRawMessage(): RawMessage? {
        return blockingQueue.take()
    }

    fun onReceive(msg: RawMessage) {
        blockingQueue.put(msg)
    }
}



interface JupyterWsSocketManager : JupyterBaseSockets, JupyterSocketManagerBase, Closeable {
    override val heartbeat: CallbackEnabledJupyterSocket
    override val shell: CallbackEnabledJupyterSocket
    override val control: CallbackEnabledJupyterSocket
    override val stdin: CallbackEnabledJupyterSocket
    override val iopub: CallbackEnabledJupyterSocket
}


private val offset1 = byteArrayOf(0, 0, 0, 4)

class WsKernelAddress(val port: Int) : KernelAddress

fun startWebSocketServer(replSettings: DefaultReplSettings) {
    val serverFuture = CompletableFuture<WebSocketServer>()
    val address = replSettings.kernelConfig.address
    require(address is WsKernelAddress) { "Wrong KernelAddress type" }
    JupyterWebSocketServer(
        address = InetSocketAddress(/* port = */ address.port),
        loggerFactory = replSettings.loggerFactory,
        onConnectionOpen = { webSocket ->
            startServer(replSettings,
                createConnection = { loggerFactory, config ->
                    JupyterWsConnectionImpl(webSocket, loggerFactory)
                },
                getSocketManager = JupyterWsConnectionImpl::socketManager,
                listen = { conn, logger ->
                val server = serverFuture.get()
                    try {
                        server.run()
                    } finally {
                        server.stop()
                    }
                    server.run()
                }
            )
        }
    )
}
