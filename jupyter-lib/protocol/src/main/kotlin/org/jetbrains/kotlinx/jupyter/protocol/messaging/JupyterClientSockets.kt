package org.jetbrains.kotlinx.jupyter.protocol.messaging

import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import java.io.Closeable

/**
 * The client side of the sockets.
 * To be used when the client wants to handle incoming messages with callbacks.
 * To receive socket messages explicitly, use [org.jetbrains.kotlinx.jupyter.protocol.sendReceive].
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
