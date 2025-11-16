package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import java.io.Closeable

interface ZmqSocketWithCancellation : Closeable {
    @Throws(InterruptedException::class)
    fun recvMultipart(): Sequence<ByteArray>

    fun sendMultipart(message: Sequence<ByteArray>)

    fun makeRelaxed()

    fun subscribe(topic: ByteArray): Boolean
}

@Throws(InterruptedException::class)
fun ZmqSocketWithCancellation.recv(): ByteArray = recvMultipart().single()

fun ZmqSocketWithCancellation.send(data: ByteArray) = sendMultipart(sequenceOf(data))

interface JupyterZmqSocket :
    JupyterSendReceiveSocket,
    Closeable {
    val zmqSocket: ZmqSocketWithCancellation

    // Used on the server side
    fun bind(): Boolean

    // Used on the client side
    fun connect(): Boolean
}
