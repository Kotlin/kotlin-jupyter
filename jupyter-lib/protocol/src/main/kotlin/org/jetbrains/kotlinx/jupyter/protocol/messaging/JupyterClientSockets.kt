package org.jetbrains.kotlinx.jupyter.protocol.messaging

import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import java.io.Closeable

/**
 * The client side of the sockets.
 * To be used when the client wants to handle incoming messages with callbacks.
 * For receiving socket messages explicitly, use [JupyterClientReceiveSockets].
 */
interface JupyterClientSockets : Closeable {
    val shell: JupyterCallbackBasedSocket
    val control: JupyterCallbackBasedSocket
    val ioPub: JupyterCallbackBasedSocket
    val stdin: JupyterCallbackBasedSocket
}

interface JupyterClientSocketManager {
    fun open(configParams: KernelJupyterParams): JupyterClientSockets
}

/**
 * The client side of the sockets.
 * To be used when the client wants to receive socket messages explicitly.
 * For handling incoming messages with callbacks, use [JupyterClientSockets].
 */
interface JupyterClientReceiveSockets : Closeable {
    val shell: JupyterSendReceiveSocket
    val control: JupyterSendReceiveSocket
    val ioPub: JupyterReceiveSocket
    val stdin: JupyterSendReceiveSocket
}

interface JupyterClientReceiveSocketManager {
    fun open(configParams: KernelJupyterParams): JupyterClientReceiveSockets
}
