package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import java.util.concurrent.ArrayBlockingQueue

fun JupyterZmqSocket.zmqSendReceive(messageBufferCapacity: Int = 256): JupyterZmqReceiveSocket =
    JupyterZmqReceiveSocketImpl(this, messageBufferCapacity)

private class JupyterZmqReceiveSocketImpl(
    private val delegate: JupyterZmqSocket,
    messageBufferCapacity: Int,
) : JupyterZmqReceiveSocket,
    JupyterZmqSocket by delegate {
    val receivableMessages = ArrayBlockingQueue<List<ByteArray>>(messageBufferCapacity)

    init {
        onBytesReceived { receivableMessages.put(it) }
    }

    override fun receiveBytes(): List<ByteArray> = receivableMessages.take()

    override fun receiveRawMessage(): RawMessage? = convertToRawMessage(receiveBytes())
}
