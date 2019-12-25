package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.zeromq.ZMQ
import java.io.Closeable
import java.security.SignatureException
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JupyterConnection(val config: KernelConfig): Closeable {

    inner class Socket(val socket: JupyterSockets, type: Int) : ZMQ.Socket(context, type) {
        val name: String get() = socket.name
        init {
            val port = config.ports[socket.ordinal]
            bind("${config.transport}://*:$port")
            if (type == ZMQ.PUB) {
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

        inline fun onMessage(body: Socket.(Message) -> Unit) = recv(ZMQ.DONTWAIT)?.let { receiveMessage(it)?.let { body(it) } }

        fun sendStatus(status: String, msg: Message) {
            connection.iopub.send(makeReplyMessage(msg, "status", content = jsonObject("execution_state" to status)))
        }

        fun sendWrapped(incomingMessage: Message, msg: Message) {
            sendStatus("busy", incomingMessage)
            send(msg)
            sendStatus("idle", incomingMessage)
        }

        fun send(msg: Message) {
            log.debug("[$name] snd>: $msg")
            sendMessage(msg, hmac)
        }

        fun receiveMessage(start: ByteArray): Message? {
            try {
                val msg = receiveMessage(start, hmac)
                log.debug("[$name] >rcv: $msg")
                return msg
            }
            catch (e: SignatureException) {
                log.error("[$name] ${e.message}")
                return null
            }
        }

        val connection: JupyterConnection = this@JupyterConnection
    }

    inner class StdinInputStream : java.io.InputStream() {
        private var currentBuf: ByteArray? = null
        private var currentBufPos = 0

        fun getInput(): String {
            stdin.send(makeReplyMessage(contextMessage!!, "input_request",
                    content = jsonObject("prompt" to "stdin:")))
            val msg = stdin.receiveMessage(stdin.recv())
            val input = msg?.content?.get("value")
            if (msg == null || msg.header?.get("msg_type")?.equals("input_reply") != true || input == null || input !is String)
                throw UnsupportedOperationException("Unexpected input message $msg")
            return input
        }

        @Synchronized
        override fun read(): Int {
            if (currentBuf == null) {
                currentBuf = getInput().toByteArray()
                currentBufPos = 0
            }
            if (currentBufPos >= currentBuf!!.size) {
                currentBuf = null
                return -1
            }
            currentBufPos.inc()
            return currentBuf!![currentBufPos - 1].toInt()
        }

        @Synchronized
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (currentBuf == null) {
                currentBuf = getInput().toByteArray()
                currentBufPos = 0
            }
            val lenLeft = currentBuf!!.size - currentBufPos
            if (lenLeft <= 0) {
                currentBuf = null
                return -1
            }
            val lenToRead = if (lenLeft > len) len else lenLeft
            for (i in 0 .. (lenToRead - 1)) {
                b.set(off + i,currentBuf!![currentBufPos + i])
            }
            currentBufPos += lenToRead
            return lenToRead
        }
    }

    private val hmac = HMAC(config.signatureScheme.replace("-", ""), config.signatureKey)
    private val context = ZMQ.context(1)

    val disposable = Disposable { }

    val heartbeat = Socket(JupyterSockets.hb, ZMQ.REP)
    val shell = Socket(JupyterSockets.shell, ZMQ.ROUTER)
    val control = Socket(JupyterSockets.control, ZMQ.ROUTER)
    val stdin = Socket(JupyterSockets.stdin, ZMQ.ROUTER)
    val iopub = Socket(JupyterSockets.iopub, ZMQ.PUB)

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

class HMAC(algo: String, key: String?) {
    val mac = if (key?.isNotBlank() ?: false) Mac.getInstance(algo) else null

    init {
        mac?.init(SecretKeySpec(key!!.toByteArray(), algo))
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
    msg.id.forEach { sendMore(it) }
    sendMore(DELIM)
    val signableMsg = listOf(msg.header, msg.parentHeader, msg.metadata, msg.content)
            .map { it?.toJsonString(prettyPrint = false)?.toByteArray() ?: emptyJsonObjectStringBytes }
    sendMore(hmac(signableMsg) ?: "")
    signableMsg.take(signableMsg.size - 1).forEach { sendMore(it) }
    send(signableMsg.last())
}

fun ZMQ.Socket.receiveMessage(start: ByteArray, hmac: HMAC): Message? {
    val ids = listOf(start) + generateSequence { recv() }.takeWhile { !Arrays.equals(it, DELIM) }
    val sig = recvStr().toLowerCase()
    val header = recv()
    val parentHeader = recv()
    val metadata = recv()
    val content = recv()
    val calculatedSig = hmac(header, parentHeader, metadata, content)

    if (calculatedSig != null && sig != calculatedSig)
        throw SignatureException("Invalid signature: expected $calculatedSig, received $sig - $ids")

    fun ByteArray.parseJson(): JsonObject =
            Parser().parse(this.inputStream()) as JsonObject

    return Message(ids,
            header.parseJson(),
            parentHeader.parseJson(),
            metadata.parseJson(),
            content.parseJson())
}
