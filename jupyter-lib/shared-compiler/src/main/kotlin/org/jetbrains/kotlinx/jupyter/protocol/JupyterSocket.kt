package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.slf4j.Logger
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

interface JupyterSendReceiveSocket : JupyterSendSocket, JupyterReceiveSocket

interface JupyterCallbackBasedSocket : JupyterSendSocket {
    fun onRawMessage(callback: RawMessageCallback)
}

interface JupyterCallbackBasedSocketImpl : JupyterCallbackBasedSocket {
    /**
     * Blocks until a message arrives.
     * Runs all registered callbacks on it when it happens.
     * If an error occurs during the receipt of the message, the message is skipped and the error is logged.
     */
    @Throws(InterruptedException::class)
    fun receiveMessageAndRunCallbacks()
}

/** The callbacks won't be handled unless [JupyterCallbackBasedSocketImpl.receiveMessageAndRunCallbacks] is called!  */
fun JupyterSendReceiveSocket.callbackBased(logger: Logger): JupyterCallbackBasedSocketImpl {
    val callbackHandler = CallbackHandler(logger)

    return object : JupyterCallbackBasedSocketImpl, JupyterSendSocket by this {
        override fun onRawMessage(callback: RawMessageCallback) = callbackHandler.addCallback(callback)

        override fun receiveMessageAndRunCallbacks() {
            val message = receiveRawMessage()
            if (message != null) {
                callbackHandler.runCallbacks(message)
            }
        }
    }
}


/**
 * Receiving messages when the buffer is full will block the incoming message processor thread
 * (the thread which runs [JupyterCallbackBasedSocketImpl.receiveMessageAndRunCallbacks]).
 */
fun JupyterCallbackBasedSocket.sendReceive(messageBufferCapacity: Int = 256): JupyterSendReceiveSocket {
    return object : JupyterSendReceiveSocket, JupyterSendSocket by this {
        val receivableMessages = ArrayBlockingQueue<RawMessage>(messageBufferCapacity)

        init {
            onRawMessage { receivableMessages.put(it) }
        }

        override fun receiveRawMessage(): RawMessage? {
            return receivableMessages.take()
        }
    }
}
