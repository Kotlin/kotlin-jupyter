package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.InputRequest
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactory
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.makeReplyMessage
import org.jetbrains.kotlinx.jupyter.messaging.sendMessage
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.protocol.AbstractJupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.openServerSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class StdinInputStream(
    private val stdinSocket: JupyterSocket,
    private val messageFactory: MessageFactory,
) : InputStream() {
    private var currentBuf: ByteArray? = null
    private var currentBufPos = 0

    private fun getInput(): String {
        stdinSocket.sendMessage(
            messageFactory.makeReplyMessage(MessageType.INPUT_REQUEST, InputRequest("stdin:")),
        )
        val msg = stdinSocket.receiveMessage()
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

class JupyterConnectionImpl(
    override val config: KernelConfig,
) : AbstractJupyterConnection(), JupyterConnectionInternal, Closeable {

    override val messageFactory: MessageFactory = MessageFactoryImpl()

    override val socketManager: JupyterSocketManager = JupyterSocketManagerImpl { socketInfo, context ->
        openServerSocket(
            socketInfo,
            context,
            config,
        )
    }

    override val stdinIn = StdinInputStream(socketManager.stdin, messageFactory)

    override val executor: JupyterExecutor = JupyterExecutorImpl()

    override fun close() {
        socketManager.close()
    }
}

fun JupyterSocket.receiveMessage(): Message? {
    val rawMessage = receiveRawMessage()
    return rawMessage?.toMessage()
}

fun JupyterSocket.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}

object DisabledStdinInputStream : InputStream() {
    override fun read(): Int {
        throw IOException("Input from stdin is unsupported by the client")
    }
}
