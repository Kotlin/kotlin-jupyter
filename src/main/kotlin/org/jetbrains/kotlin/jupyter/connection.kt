package org.jetbrains.kotlin.jupyter

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.io.Closeable
import java.io.IOException
import java.security.SignatureException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

class JupyterConnection(val config: KernelConfig) : Closeable {

    inner class Socket(private val socket: JupyterSockets, type: SocketType = socket.zmqKernelType) : ZMQ.Socket(context, type) {
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

        inline fun onData(body: Socket.(ByteArray) -> Unit) = recv(ZMQ.DONTWAIT)?.let { body(it) }

        inline fun onMessage(body: Socket.(Message) -> Unit) = recv(ZMQ.DONTWAIT)?.let { bytes -> receiveMessage(bytes)?.let { body(it) } }

        fun sendStatus(status: KernelStatus, msg: Message) {
            connection.iopub.send(makeReplyMessage(msg, MessageType.STATUS, content = StatusReply(status)))
        }

        fun sendWrapped(incomingMessage: Message, msg: Message) {
            sendStatus(KernelStatus.BUSY, incomingMessage)
            send(msg)
            sendStatus(KernelStatus.IDLE, incomingMessage)
        }

        fun sendOut(msg: Message, stream: JupyterOutType, text: String) {
            send(makeReplyMessage(msg, header = makeHeader(MessageType.STREAM, msg), content = StreamResponse(stream.optionName(), text)))
        }

        fun send(msg: Message) {
            log.debug("[$name] snd>: $msg")
            sendMessage(msg, hmac)
        }

        fun receiveMessage(start: ByteArray): Message? {
            return try {
                val msg = receiveMessage(start, hmac)
                log.debug("[$name] >rcv: $msg")
                msg
            } catch (e: SignatureException) {
                log.error("[$name] ${e.message}")
                null
            }
        }

        val connection: JupyterConnection = this@JupyterConnection
    }

    inner class StdinInputStream : java.io.InputStream() {
        private var currentBuf: ByteArray? = null
        private var currentBufPos = 0

        private fun getInput(): String {
            stdin.send(
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

    val heartbeat = Socket(JupyterSockets.hb)
    val shell = Socket(JupyterSockets.shell)
    val control = Socket(JupyterSockets.control)
    val stdin = Socket(JupyterSockets.stdin)
    val iopub = Socket(JupyterSockets.iopub)

    val stdinIn = StdinInputStream()

    var contextMessage: Message? = null

    override fun close() {
        heartbeat.close()
        shell.close()
        control.close()
        stdin.close()
        iopub.close()
        context.close()
    }
}

private val MESSAGE_DELIMITER: ByteArray = "<IDS|MSG>".map { it.toByte() }.toByteArray()

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

fun ZMQ.Socket.sendMessage(msg: Message, hmac: HMAC) {
    synchronized(this) {
        msg.id.forEach { sendMore(it) }
        sendMore(MESSAGE_DELIMITER)

        val dataJson = Json.encodeToJsonElement(msg.data).jsonObject
        val signableMsg = listOf("header", "parent_header", "metadata", "content")
            .map { fieldName -> dataJson[fieldName]?.let { Json.encodeToString(it) }?.toByteArray() ?: emptyJsonObjectStringBytes }
        sendMore(hmac(signableMsg) ?: "")
        signableMsg.take(signableMsg.size - 1).forEach { sendMore(it) }
        send(signableMsg.last())
    }
}

fun ZMQ.Socket.receiveMessage(start: ByteArray, hmac: HMAC): Message {
    val ids = listOf(start) + generateSequence { recv() }.takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
    val sig = recvStr().toLowerCase()
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

    fun JsonElement.orEmptyObject() = if (this is JsonNull) emptyJsonObject else this

    val dataJson = jsonObject(
        "header" to header.parseJson(),
        "parent_header" to parentHeader.parseJson(),
        "metadata" to metadata.parseJson().orEmptyObject(),
        "content" to content.parseJson().orEmptyObject()
    )

    log.info(Json { prettyPrint = true }.encodeToString(dataJson))

    val data = Json.decodeFromJsonElement<MessageData>(dataJson)

    return Message(
        ids,
        data
    )
}

object DisabledStdinInputStream : java.io.InputStream() {
    override fun read(): Int {
        throw IOException("Input from stdin is unsupported by the client")
    }
}
