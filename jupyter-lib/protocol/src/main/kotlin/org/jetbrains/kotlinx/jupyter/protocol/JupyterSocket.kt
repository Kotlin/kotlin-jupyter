package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import java.util.concurrent.ArrayBlockingQueue

interface JupyterSendSocket {
    fun sendRawMessage(msg: RawMessage)
}

interface JupyterReceiveSocket {
    /**
     * Blocks until a message arrives.
     * If an error occurs during the receipt of the message, null is returned and the error is logged.
     * Returns null when an error occurs during the receipt of the message (the error will be logged).
     */
    @Throws(InterruptedException::class)
    fun receiveRawMessage(): RawMessage?
}

interface JupyterSendReceiveSocket :
    JupyterSendSocket,
    JupyterReceiveSocket

interface JupyterCallbackBasedSocket : JupyterSendSocket {
    fun onRawMessage(callback: RawMessageCallback)
}

/**
 * Receiving messages when the buffer is full will block the incoming message processor thread
 */
fun JupyterCallbackBasedSocket.sendReceive(messageBufferCapacity: Int = 256): JupyterSendReceiveSocket =
    object : JupyterSendReceiveSocket, JupyterSendSocket by this {
        val receivableMessages = ArrayBlockingQueue<RawMessage>(messageBufferCapacity)

        init {
            onRawMessage { receivableMessages.put(it) }
        }

        override fun receiveRawMessage(): RawMessage? = receivableMessages.take()
    }
