package org.jetbrains.kotlinx.jupyter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocket
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.header
import org.jetbrains.kotlinx.jupyter.api.libraries.type
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.InputRequest
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.JupyterServerSocket
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.RawMessageImpl
import org.jetbrains.kotlinx.jupyter.messaging.StatusReply
import org.jetbrains.kotlinx.jupyter.messaging.emptyJsonObjectStringBytes
import org.jetbrains.kotlinx.jupyter.messaging.jsonObject
import org.jetbrains.kotlinx.jupyter.messaging.makeJsonHeader
import org.jetbrains.kotlinx.jupyter.messaging.makeReplyMessage
import org.jetbrains.kotlinx.jupyter.messaging.sendMessage
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.io.Closeable
import java.io.IOException
import java.security.SignatureException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.math.min

typealias SocketMessageCallback = JupyterConnectionImpl.Socket.(Message) -> Unit
typealias SocketRawMessageCallback = JupyterConnectionImpl.Socket.(RawMessage) -> Unit

class JupyterConnectionImpl(
    val config: KernelConfig
) : JupyterConnectionInternal, Closeable {

    private var _messageId: List<ByteArray> = listOf(byteArrayOf(1))
    override val messageId: List<ByteArray> get() = _messageId

    private var _sessionId = ""
    override val sessionId: String get() = _sessionId

    private var _username = ""
    override val username: String get() = _username

    inner class Socket(private val socket: JupyterSocketInfo, type: SocketType = socket.zmqKernelType) : ZMQ.Socket(context, type), JupyterServerSocket {
        val name: String get() = socket.name
        init {
            val port = config.ports[socket.ordinal]
            bind("${config.transport}://*:$port")
            if (type == SocketType.PUB) {
                // Workaround to prevent losing few first messages on kernel startup
                // For more information on losing messages see this scheme:
                // http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
                // It seems we cannot do correct sync because messaging protocol
                // doesn't support this. Value of 500 ms was chosen experimentally.
                Thread.sleep(500)
            }
            log.debug("[$name] listen: ${config.transport}://*:$port")
        }

        private val callbacks = mutableSetOf<SocketRawMessageCallback>()

        fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback {
            callbacks.add(callback)
            return callback
        }

        fun onMessage(callback: SocketMessageCallback): SocketRawMessageCallback {
            return onRawMessage { rawMessage ->
                callback(rawMessage.toMessage())
            }
        }

        fun removeCallback(callback: SocketRawMessageCallback) {
            callbacks.remove(callback)
        }

        inline fun onData(body: Socket.(ByteArray) -> Unit) = recv()?.let { body(it) }

        fun runCallbacksOnMessage() = recv()?.let { bytes ->
            receiveRawMessage(bytes)?.let { message ->
                callbacks.forEach { callback ->
                    try {
                        callback(message)
                    } catch (e: Throwable) {
                        log.error("Exception thrown while processing a message", e)
                    }
                }
            }
        }

        fun sendStatus(status: KernelStatus, msg: Message) {
            connection.iopub.sendMessage(makeReplyMessage(msg, MessageType.STATUS, content = StatusReply(status)))
        }

        fun sendWrapped(incomingMessage: Message, msg: Message) {
            sendStatus(KernelStatus.BUSY, incomingMessage)
            sendMessage(msg)
            sendStatus(KernelStatus.IDLE, incomingMessage)
        }

        override fun sendRawMessage(msg: RawMessage) {
            log.debug("[$name] snd>: $msg")
            sendRawMessage(msg, hmac)
        }

        fun receiveMessage(start: ByteArray): Message? {
            val rawMessage = receiveRawMessage(start)
            return rawMessage?.toMessage()
        }

        private fun receiveRawMessage(start: ByteArray): RawMessage? {
            return try {
                val msg = receiveRawMessage(start, hmac)
                log.debug("[$name] >rcv: $msg")
                msg
            } catch (e: SignatureException) {
                log.error("[$name] ${e.message}")
                null
            }
        }

        override val connection: JupyterConnectionImpl = this@JupyterConnectionImpl
    }

    inner class StdinInputStream : java.io.InputStream() {
        private var currentBuf: ByteArray? = null
        private var currentBufPos = 0

        private fun getInput(): String {
            stdin.sendMessage(
                makeReplyMessage(
                    contextMessage!!,
                    MessageType.INPUT_REQUEST,
                    content = InputRequest("stdin:")
                )
            )
            val msg = stdin.receiveMessage(stdin.recv())
            val content = msg?.data?.content as? InputReply

            return content?.value ?: throw UnsupportedOperationException("Unexpected input message $msg")
        }

        private fun initializeCurrentBuf(): ByteArray {
            val buf = currentBuf
            return if (buf != null) {
                buf
            } else {
                val newBuf = getInput().toByteArray()
                currentBuf = newBuf
                currentBufPos = 0
                newBuf
            }
        }

        @Synchronized
        override fun read(): Int {
            val buf = initializeCurrentBuf()
            if (currentBufPos >= buf.size) {
                currentBuf = null
                return -1
            }

            return buf[currentBufPos++].toInt()
        }

        @Synchronized
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val buf = initializeCurrentBuf()
            val lenLeft = buf.size - currentBufPos
            if (lenLeft <= 0) {
                currentBuf = null
                return -1
            }
            val lenToRead = min(len, lenLeft)
            for (i in 0 until lenToRead) {
                b[off + i] = buf[currentBufPos + i]
            }
            currentBufPos += lenToRead
            return lenToRead
        }
    }

    private val hmac = HMAC(config.signatureScheme.replace("-", ""), config.signatureKey)
    private val context = ZMQ.context(1)

    override val heartbeat = Socket(JupyterSocketInfo.HB)
    override val shell = Socket(JupyterSocketInfo.SHELL)
    override val control = Socket(JupyterSocketInfo.CONTROL)
    override val stdin = Socket(JupyterSocketInfo.STDIN)
    override val iopub = Socket(JupyterSocketInfo.IOPUB)

    private fun fromSocketName(socket: JupyterSocket): Socket {
        return when (socket) {
            JupyterSocket.HB -> heartbeat
            JupyterSocket.SHELL -> shell
            JupyterSocket.CONTROL -> control
            JupyterSocket.STDIN -> stdin
            JupyterSocket.IOPUB -> iopub
        }
    }

    private val callbacks = mutableMapOf<RawMessageCallback, SocketRawMessageCallback>()

    override fun addMessageCallback(callback: RawMessageCallback): RawMessageCallback {
        val socket = fromSocketName(callback.socket)
        val socketCallback: SocketRawMessageCallback = { message ->
            if (message.type == callback.messageType) {
                callback.action(message)
            }
        }
        callbacks[callback] = socket.onRawMessage(socketCallback)
        return callback
    }

    override fun removeMessageCallback(callback: RawMessageCallback) {
        val socketCallback = callbacks[callback] ?: return
        val socket = fromSocketName(callback.socket)
        socket.removeCallback(socketCallback)
    }

    fun updateSessionInfo(message: Message) {
        val header = message.data.header ?: return
        header.session?.let { _sessionId = it }
        header.username?.let { _username = it }
        _messageId = message.id
    }

    override fun send(socketName: JupyterSocket, message: RawMessage) {
        val socket = fromSocketName(socketName)
        socket.sendRawMessage(message)
    }

    override fun sendReply(socketName: JupyterSocket, parentMessage: RawMessage, type: String, content: JsonObject, metadata: JsonObject?) {
        val message = RawMessageImpl(
            parentMessage.id,
            JsonObject(
                mapOf(
                    "header" to makeJsonHeader(type, parentMessage),
                    "parent_header" to (parentMessage.header ?: Json.EMPTY),
                    "metadata" to (metadata ?: Json.EMPTY),
                    "content" to content
                )
            )
        )
        send(socketName, message)
    }

    val stdinIn = StdinInputStream()

    var contextMessage: Message? = null

    private val currentExecutions = HashSet<Thread>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    data class ConnectionExecutionResult<T>(
        val result: T?,
        val throwable: Throwable?,
        val isInterrupted: Boolean,
    )

    fun <T> runExecution(body: () -> T, classLoader: ClassLoader): ConnectionExecutionResult<T> {
        var execRes: T? = null
        var execException: Throwable? = null
        val execThread = thread(contextClassLoader = classLoader) {
            try {
                execRes = body()
            } catch (e: Throwable) {
                execException = e
            }
        }
        currentExecutions.add(execThread)
        execThread.join()
        currentExecutions.remove(execThread)

        val exception = execException
        val isInterrupted = exception is ThreadDeath ||
            (exception is ReplException && exception.cause is ThreadDeath)
        return ConnectionExecutionResult(execRes, exception, isInterrupted)
    }

    /**
     * We cannot use [Thread.interrupt] here because we have no way
     * to control the code user executes. [Thread.interrupt] will do nothing for
     * the simple calculation (like `while (true) 1`). Consider replacing with
     * something more smart in the future.
     */
    fun interruptExecution() {
        @Suppress("deprecation")
        while (currentExecutions.isNotEmpty()) {
            val execution = currentExecutions.firstOrNull()
            execution?.stop()
            currentExecutions.remove(execution)
        }
    }

    fun launchJob(runnable: suspend CoroutineScope.() -> Unit) {
        coroutineScope.launch(block = runnable)
    }

    override fun close() {
        heartbeat.close()
        shell.close()
        control.close()
        stdin.close()
        iopub.close()
        context.close()
    }
}

private val MESSAGE_DELIMITER: ByteArray = "<IDS|MSG>".map { it.code.toByte() }.toByteArray()

class HMAC(algorithm: String, key: String?) {
    private val mac = if (key?.isNotBlank() == true) Mac.getInstance(algorithm) else null

    init {
        mac?.init(SecretKeySpec(key!!.toByteArray(), algorithm))
    }

    @Synchronized
    operator fun invoke(data: Iterable<ByteArray>): String? =
        mac?.let { mac ->
            data.forEach { mac.update(it) }
            mac.doFinal().toHexString()
        }

    operator fun invoke(vararg data: ByteArray): String? = invoke(data.asIterable())
}

fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

fun ZMQ.Socket.sendRawMessage(msg: RawMessage, hmac: HMAC) {
    synchronized(this) {
        msg.id.forEach { sendMore(it) }
        sendMore(MESSAGE_DELIMITER)

        val dataJson = msg.data.jsonObject
        val signableMsg = listOf("header", "parent_header", "metadata", "content")
            .map { fieldName -> dataJson[fieldName]?.let { Json.encodeToString(it) }?.toByteArray() ?: emptyJsonObjectStringBytes }
        sendMore(hmac(signableMsg) ?: "")
        for (i in 0 until (signableMsg.size - 1)) {
            sendMore(signableMsg[i])
        }
        send(signableMsg.last())
    }
}

fun ZMQ.Socket.sendMessage(msg: Message, hmac: HMAC) {
    sendRawMessage(msg.toRawMessage(), hmac)
}

fun ZMQ.Socket.receiveRawMessage(start: ByteArray, hmac: HMAC): RawMessage {
    val ids = listOf(start) + generateSequence { recv() }.takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
    val sig = recvStr().lowercase()
    val header = recv()
    val parentHeader = recv()
    val metadata = recv()
    val content = recv()
    val calculatedSig = hmac(header, parentHeader, metadata, content)

    if (calculatedSig != null && sig != calculatedSig) {
        throw SignatureException("Invalid signature: expected $calculatedSig, received $sig - $ids")
    }

    fun ByteArray.parseJson(): JsonElement {
        val json = Json.decodeFromString<JsonElement>(this.toString(Charsets.UTF_8))
        return if (json is JsonObject && json.isEmpty()) JsonNull else json
    }

    fun JsonElement.orEmptyObject() = if (this is JsonNull) Json.EMPTY else this

    val dataJson = jsonObject(
        "header" to header.parseJson(),
        "parent_header" to parentHeader.parseJson(),
        "metadata" to metadata.parseJson().orEmptyObject(),
        "content" to content.parseJson().orEmptyObject()
    )

    return RawMessageImpl(
        ids,
        dataJson
    )
}

object DisabledStdinInputStream : java.io.InputStream() {
    override fun read(): Int {
        throw IOException("Input from stdin is unsupported by the client")
    }
}
