package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import java.io.Closeable

interface ZmqSocketWithCancellation : Closeable {
    @Throws(InterruptedException::class)
    fun recvMultipart(): Sequence<ByteArray>

    fun sendMultipart(message: Sequence<ByteArray>)

    fun subscribe(topic: ByteArray): Boolean
}

interface JupyterZmqSocket :
    JupyterSendReceiveSocket,
    Closeable {
    val zmqSocket: ZmqSocketWithCancellation

    // Used on the server side
    fun bind(): Boolean

    // Used on the client side
    fun connect(): Boolean
}
