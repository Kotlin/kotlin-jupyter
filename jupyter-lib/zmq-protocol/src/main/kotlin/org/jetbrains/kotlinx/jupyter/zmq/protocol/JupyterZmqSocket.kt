package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import java.io.Closeable

/**
 * Callback-based ZMQ socket.
 * Additionally to processing [RawMessage]s, allows subscribing ro receiving of raw
 * bytes via [onBytesReceived] callback and sending raw bytes via [sendBytes] method.
 */
interface JupyterZmqSocket :
    JupyterCallbackBasedSocket,
    Closeable {
    fun onBytesReceived(callback: (List<ByteArray>) -> Unit)

    fun sendBytes(message: List<ByteArray>)

    /**
     * Converts raw bytes (i.e., those received via [onBytesReceived]) to [RawMessage]
     * Might be used in bytes-based callbacks to "upgrade" the message
     */
    fun convertToRawMessage(zmqMessage: List<ByteArray>): RawMessage?

    /**
     * Used on the server side to start listening for messages
     * All message callbacks should be registered before calling this method,
     * otherwise there is a risk of missing messages
     */
    fun bind()

    // Used on the client side
    fun connect()
}
