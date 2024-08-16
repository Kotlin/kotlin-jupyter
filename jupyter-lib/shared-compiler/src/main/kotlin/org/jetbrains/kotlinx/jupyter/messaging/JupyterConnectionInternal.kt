package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketManagerBase
import java.io.Closeable

/**
 * Interface describing the sockets available in a Jupyter Kernel.
 *
 * See https://jupyter-client.readthedocs.io/en/stable/messaging.html#introduction
 * for a description of them.
 *
 * Each of these will match a corresponding type in [org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType]
 */
interface JupyterBaseSockets {
    val heartbeat: JupyterSocketBase
    val shell: JupyterSocketBase
    val control: JupyterSocketBase
    val stdin: JupyterSocketBase
    val iopub: JupyterSocketBase
}

/**
 * Interface responsible for controlling the lifecycle of kernel sockets.
 */
interface JupyterSocketManager : JupyterBaseSockets, JupyterSocketManagerBase, Closeable {
    override val heartbeat: JupyterSocket
    override val shell: JupyterSocket
    override val control: JupyterSocket
    override val stdin: JupyterSocket
    override val iopub: JupyterSocket
}

interface JupyterConnectionInternal : JupyterConnection {
    val socketManager: JupyterSocketManager
}

fun JupyterSocketBase.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}
