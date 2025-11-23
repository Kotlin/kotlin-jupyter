package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket

/**
 * ZMQ socket that allows receiving messages in a blocking manner
 */
interface JupyterZmqReceiveSocket :
    JupyterZmqSocket,
    JupyterSendReceiveSocket {
    @Throws(InterruptedException::class)
    fun receiveBytes(): List<ByteArray>
}
