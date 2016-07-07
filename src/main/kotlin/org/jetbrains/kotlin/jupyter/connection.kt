package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.zeromq.ZMQ
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.io.PrintStream
import java.security.SignatureException
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JupyterConnection(val config: ConnectionConfig): Closeable {

    inner class Socket(val socket: JupyterSockets, type: Int) : ZMQ.Socket(context, type) {
        val name: String get() = socket.name
        init {
            val port = config.ports[socket.ordinal]
            bind("${config.transport}://*:$port")
            log.debug("[$name] listen: ${config.transport}://*:$port")
        }

        inline fun onData(body: Socket.(ByteArray) -> Unit) = recv(ZMQ.DONTWAIT)?.let { body(it) }

        inline fun onMessage(body: Socket.(Message) -> Unit) = recv(ZMQ.DONTWAIT)?.let { receiveMessage(it)?.let { body(it) } }

        fun send(msg: Message): Unit {
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

    private inner class IopubOutputStream(val streamName: String) : java.io.OutputStream() {
        fun sendToSocket(text: String) {
            iopub.send(Message(
                    header = makeHeader("stream", contextMessage),
                    content = jsonObject(
                            "name" to streamName,
                            "text" to text)))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val buf = ByteArrayOutputStream()
            buf.write(b, off, len)
            sendToSocket(buf.toString())
        }

        override fun write(b: Int) {
            val buf = ByteArrayOutputStream()
            buf.write(b)
            sendToSocket(buf.toString())
        }
    }

    private val hmac = HMAC(config.signatureScheme.replace("-", ""), config.signatureKey)
    private val context = ZMQ.context(1)

    val heartbeat = Socket(JupyterSockets.hb, ZMQ.REP)
    val shell = Socket(JupyterSockets.shell, ZMQ.ROUTER)
    val control = Socket(JupyterSockets.control, ZMQ.ROUTER)
    val stdin = Socket(JupyterSockets.stdin, ZMQ.ROUTER)
    val iopub = Socket(JupyterSockets.iopub, ZMQ.PUB)

    val iopubOut = PrintStream(IopubOutputStream("stdout"))
    val iopubErr = PrintStream(IopubOutputStream("stderr"))

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

fun JupyterConnection.Socket.logWireMessage(msg: ByteArray) {
    log.debug("[$name] >in: ${String(msg)}")
}

fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

fun ZMQ.Socket.sendMessage(msg: Message, hmac: HMAC): Unit {
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
