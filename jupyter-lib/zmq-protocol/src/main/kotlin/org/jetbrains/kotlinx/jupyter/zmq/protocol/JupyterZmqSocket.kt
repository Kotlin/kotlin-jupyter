package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import java.io.Closeable

interface ZmqSocketWithCancellation : Closeable {
    @Throws(InterruptedException::class)
    fun recv(): ByteArray

    @Throws(InterruptedException::class)
    fun recvString(): String

    fun sendMore(data: ByteArray): Boolean

    fun sendMore(data: String): Boolean

    fun send(data: ByteArray): Boolean

    fun send(data: String): Boolean

    fun makeRelaxed()

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
