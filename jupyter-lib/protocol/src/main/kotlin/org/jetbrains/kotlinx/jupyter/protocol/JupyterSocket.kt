package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import java.util.concurrent.ArrayBlockingQueue

interface JupyterSendSocket {
    fun sendRawMessage(msg: RawMessage)
}

interface JupyterReceiveSocket {
    /** Blocks until a message arrives. */
    @Throws(InterruptedException::class)
    fun receiveRawMessage(): RawMessage
}

interface JupyterSendReceiveSocket :
    JupyterSendSocket,
    JupyterReceiveSocket

interface JupyterCallbackBasedSocket : JupyterSendSocket {
    fun onRawMessage(callback: (RawMessage) -> Unit)
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

        override fun receiveRawMessage(): RawMessage = receivableMessages.take()
    }
