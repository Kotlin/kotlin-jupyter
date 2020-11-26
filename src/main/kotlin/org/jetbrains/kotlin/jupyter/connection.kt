package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.utils.addToStdlib.min
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.io.Closeable
import java.security.SignatureException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

        fun sendStatus(status: String, msg: Message) {
            connection.iopub.send(makeReplyMessage(msg, "status", content = jsonObject("execution_state" to status)))
        }

        fun sendWrapped(incomingMessage: Message, msg: Message) {
            sendStatus("busy", incomingMessage)
            send(msg)
            sendStatus("idle", incomingMessage)
        }

        fun sendOut(msg: Message, stream: JupyterOutType, text: String) {
            send(makeReplyMessage(msg, header = makeHeader("stream", msg), content = jsonObject("name" to stream.optionName(), "text" to text)))
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
                    "input_request",
                    content = jsonObject("prompt" to "stdin:")
                )
            )
            val msg = stdin.receiveMessage(stdin.recv())
            val input = msg?.content?.get("value")
            if (msg == null || msg.header?.get("msg_type")?.equals("input_reply") != true || input == null || input !is String)
                throw UnsupportedOperationException("Unexpected input message $msg")
            return input
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

    private val disposable = Disposable { }

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
        Disposer.dispose(disposable)
    }
}

private val DELIM: ByteArray = "<IDS|MSG>".map { it.toByte() }.toByteArray()

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
        sendMore(DELIM)
        val signableMsg = listOf(msg.header, msg.parentHeader, msg.metadata, msg.content)
            .map { it?.toJsonString(prettyPrint = false)?.toByteArray() ?: emptyJsonObjectStringBytes }
        sendMore(hmac(signableMsg) ?: "")
        signableMsg.take(signableMsg.size - 1).forEach { sendMore(it) }
        send(signableMsg.last())
    }
}

fun ZMQ.Socket.receiveMessage(start: ByteArray, hmac: HMAC): Message? {
    val ids = listOf(start) + generateSequence { recv() }.takeWhile { !it.contentEquals(DELIM) }
    val sig = recvStr().toLowerCase()
    val header = recv()
    val parentHeader = recv()
    val metadata = recv()
    val content = recv()
    val calculatedSig = hmac(header, parentHeader, metadata, content)

    if (calculatedSig != null && sig != calculatedSig)
        throw SignatureException("Invalid signature: expected $calculatedSig, received $sig - $ids")

    fun ByteArray.parseJson(): JsonObject =
        Parser.default().parse(this.inputStream()) as JsonObject

    return Message(
        ids,
        header.parseJson(),
        parentHeader.parseJson(),
        metadata.parseJson(),
        content.parseJson()
    )
}
