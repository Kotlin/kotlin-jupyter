package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import java.io.Closeable

/**
 * Interface describing the server side of sockets available in a Jupyter Kernel.
 *
 * See https://jupyter-client.readthedocs.io/en/stable/messaging.html#introduction
 * for a description of them.
 *
 * Each of these will match a corresponding type in [org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType]
 * (but [org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType.HB] is not included,
 * as it is handled automatically)
 */
interface JupyterServerSockets {
    val shell: JupyterSendSocket
    val control: JupyterSendSocket
    val stdin: JupyterSendReceiveSocket
    val iopub: JupyterSendSocket
}

/**
 * When implementing a server, we need to add callback handlers for [shell] and [control] sockets.
 * Outside that, adding callbacks is not needed, so this interface is separate.
 */
interface JupyterServerImplSockets : JupyterServerSockets {
    override val shell: JupyterCallbackBasedSocket
    override val control: JupyterCallbackBasedSocket
}

interface JupyterSocketManager : Closeable {
    val sockets: JupyterServerImplSockets

    /**
     * This function starts listening for incoming messages and blocks the thread. It stops listening
     * when one of the callbacks (see [JupyterServerImplSockets]) throws [InterruptedException].
     */
    fun listen()
}
