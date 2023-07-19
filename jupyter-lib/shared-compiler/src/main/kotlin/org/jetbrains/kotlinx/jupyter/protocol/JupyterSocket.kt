package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
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

interface JupyterSocket : SocketWithCancellation {
    // Used on server side
    fun bind(): Boolean

    // Used on client side
    fun connect(): Boolean

    fun sendRawMessage(msg: RawMessage)
    fun receiveRawMessage(): RawMessage?
    fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback
    fun removeCallback(callback: SocketRawMessageCallback)
    fun onData(body: JupyterSocket.(ByteArray) -> Unit): Unit?
    fun runCallbacksOnMessage(): Unit?
}
