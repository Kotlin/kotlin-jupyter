package org.jetbrains.kotlinx.jupyter

import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.InputRequest
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.StatusReply
import org.jetbrains.kotlinx.jupyter.messaging.makeReplyMessage
import org.jetbrains.kotlinx.jupyter.messaging.makeSimpleMessage
import org.jetbrains.kotlinx.jupyter.messaging.sendMessage
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.protocol.AbstractJupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.HMAC
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.openServerSocket
import org.jetbrains.kotlinx.jupyter.protocol.sendRawMessage
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.ZMQ
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class JupyterConnectionImpl(
    override val config: KernelConfig
) : AbstractJupyterConnection(), JupyterConnectionInternal, Closeable {

    private var _messageId: List<ByteArray> = listOf(byteArrayOf(1))
    override val messageId: List<ByteArray> get() = _messageId

    private var _sessionId = ""
    override val sessionId: String get() = _sessionId

    private var _username = ""
    override val username: String get() = _username

    inner class StdinInputStream : InputStream() {
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
            val msg = stdin.receiveMessage(stdin.socket.recv())
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

    private fun openSocket(socketInfo: JupyterSocketInfo) = openServerSocket(
        socketInfo,
        context,
        hmac,
        config,
    )

    override val heartbeat = openSocket(JupyterSocketInfo.HB)
    override val shell = openSocket(JupyterSocketInfo.SHELL)
    override val control = openSocket(JupyterSocketInfo.CONTROL)
    override val stdin = openSocket(JupyterSocketInfo.STDIN)
    override val iopub = openSocket(JupyterSocketInfo.IOPUB)

    override fun fromSocketType(type: JupyterSocketType): JupyterSocket {
        return when (type) {
            JupyterSocketType.HB -> heartbeat
            JupyterSocketType.SHELL -> shell
            JupyterSocketType.CONTROL -> control
            JupyterSocketType.STDIN -> stdin
            JupyterSocketType.IOPUB -> iopub
        }
    }

    override fun updateSessionInfo(message: RawMessage) {
        val header = message.header
        header["session"]?.jsonPrimitive?.content?.let { _sessionId = it }
        header["username"]?.jsonPrimitive?.content?.let { _sessionId = it }
        _messageId = message.id
    }

    override fun sendStatus(status: KernelStatus, incomingMessage: RawMessage?) {
        val message = if (incomingMessage != null) makeReplyMessage(incomingMessage, MessageType.STATUS, content = StatusReply(status))
        else makeSimpleMessage(MessageType.STATUS, content = StatusReply(status))
        iopub.sendMessage(message)
    }

    override fun doWrappedInBusyIdle(incomingMessage: RawMessage?, action: () -> Unit) {
        sendStatus(KernelStatus.BUSY, incomingMessage)
        try {
            action()
        } finally {
            sendStatus(KernelStatus.IDLE, incomingMessage)
        }
    }

    override val stdinIn = StdinInputStream()

    override val debugPort: Int?
        get() = config.debugPort

    private var _contextMessage: RawMessage? = null
    override fun setContextMessage(message: RawMessage?) {
        _contextMessage = message
    }
    override val contextMessage: RawMessage? get() = _contextMessage

    override val executor: JupyterExecutor = JupyterExecutorImpl()

    override fun close() {
        heartbeat.close()
        shell.close()
        control.close()
        stdin.close()
        iopub.close()
        context.close()
    }
}

fun JupyterSocket.receiveMessage(start: ByteArray): Message? {
    val rawMessage = receiveRawMessage(start)
    return rawMessage?.toMessage()
}

fun ZMQ.Socket.sendMessage(msg: Message, hmac: HMAC) {
    sendRawMessage(msg.toRawMessage(), hmac)
}

object DisabledStdinInputStream : InputStream() {
    override fun read(): Int {
        throw IOException("Input from stdin is unsupported by the client")
    }
}
