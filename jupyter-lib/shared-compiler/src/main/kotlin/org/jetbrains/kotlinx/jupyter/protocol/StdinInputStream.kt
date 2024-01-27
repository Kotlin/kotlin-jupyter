package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.InputRequest
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactory
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.makeReplyMessage
import org.jetbrains.kotlinx.jupyter.messaging.sendMessage
import java.io.InputStream
import kotlin.math.min

class StdinInputStream(
    private val stdinSocket: JupyterSocketBase,
    private val messageFactory: MessageFactory,
) : InputStream() {
    private var currentBuf: ByteArray? = null
    private var currentBufPos = 0

    private fun getInput(): String {
        stdinSocket.sendMessage(
            messageFactory.makeReplyMessage(MessageType.INPUT_REQUEST, content = InputRequest("stdin:")),
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
