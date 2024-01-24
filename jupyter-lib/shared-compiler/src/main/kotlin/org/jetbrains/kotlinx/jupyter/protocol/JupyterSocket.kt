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

interface JupyterSocketBase {
    fun sendRawMessage(msg: RawMessage)
    fun receiveRawMessage(): RawMessage?
}

interface JupyterSocket : SocketWithCancellation, JupyterSocketBase {
    // Used on server side
    fun bind(): Boolean

    // Used on client side
    fun connect(): Boolean

    fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback
    fun removeCallback(callback: SocketRawMessageCallback)
    fun onData(body: JupyterSocket.(ByteArray) -> Unit): Unit?
    fun runCallbacksOnMessage(): Unit?
}
