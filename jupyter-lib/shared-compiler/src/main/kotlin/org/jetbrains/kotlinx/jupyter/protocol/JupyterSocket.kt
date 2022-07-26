package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.zeromq.ZMQ
import java.io.Closeable

interface JupyterSocket : Closeable {
    val socket: ZMQ.Socket

    fun bind(): Boolean
    fun connect(): Boolean

    fun sendRawMessage(msg: RawMessage)
    fun receiveRawMessage(start: ByteArray): RawMessage?
    fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback
    fun removeCallback(callback: SocketRawMessageCallback)
    fun onData(body: JupyterSocket.(ByteArray) -> Unit): Unit?
    fun runCallbacksOnMessage(): Unit?
}
