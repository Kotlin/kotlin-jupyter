package org.jetbrains.kotlinx.jupyter.streams

import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.getInput
import java.io.InputStream
import kotlin.math.min

class StdinInputStream(
    private val communicationFacility: JupyterCommunicationFacility,
) : InputStream() {
    private var currentBuf: ByteArray? = null
    private var currentBufPos = 0

    private fun initializeCurrentBuf(): ByteArray {
        val buf = currentBuf
        return if (buf != null) {
            buf
        } else {
            val newBuf = communicationFacility.getInput().toByteArray()
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
    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
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
