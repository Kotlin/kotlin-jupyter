package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import java.io.Closeable

interface SocketWithCancellation : Closeable {
    fun recv(): ByteArray

    fun recvString(): String

    fun sendMore(data: ByteArray): Boolean

    fun sendMore(data: String): Boolean

    fun send(data: ByteArray): Boolean

    fun send(data: String): Boolean

    fun makeRelaxed()

    fun subscribe(topic: ByteArray): Boolean
}

interface JupyterSocketBase {
    fun sendRawMessage(msg: RawMessage)

    /** Returns null when an error occurs during the receipt of the message. The error will be logged. */
    fun receiveRawMessage(): RawMessage?
}

interface CallbackEnabledJupyterSocket : JupyterSocketBase {
    val callbackHandler: CallbackHandler
}

fun CallbackEnabledJupyterSocket.receiveMessageAndRunCallbacks() {
    val message = receiveRawMessage()
    if (message != null) {
       callbackHandler.runCallbacksOnMessage(message)
    }
}

interface CallbackHandler {
    fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback

    fun removeCallback(callback: SocketRawMessageCallback)

    fun runCallbacksOnMessage(message: RawMessage)
}

interface JupyterZmqSocket : SocketWithCancellation, CallbackEnabledJupyterSocket {
    // Used on server side
    fun bind(): Boolean

    // Used on client side
    fun connect(): Boolean

    fun onData(body: JupyterZmqSocket.(ByteArray) -> Unit)
}

fun JupyterSocketBase.receiveMessage(): Message? {
    val rawMessage = receiveRawMessage()
    return rawMessage?.toMessage()
}
