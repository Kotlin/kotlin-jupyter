package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import java.io.Closeable

interface JupyterZmqSocket :
    JupyterSendReceiveSocket,
    Closeable {
    @Throws(InterruptedException::class)
    fun receiveMultipart(): List<ByteArray>

    fun sendMultipart(message: List<ByteArray>)

    // Used on the server side
    fun bind(): Boolean

    // Used on the client side
    fun connect(): Boolean
}
