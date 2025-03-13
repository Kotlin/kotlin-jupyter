package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocket
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
interface JupyterZmqSocketManager : JupyterBaseSockets, JupyterSocketManagerBase, Closeable {
    override val heartbeat: JupyterZmqSocket
    override val shell: JupyterZmqSocket
    override val control: JupyterZmqSocket
    override val stdin: JupyterZmqSocket
    override val iopub: JupyterZmqSocket
}

interface JupyterZmqConnectionInternal : JupyterConnection {
    val socketManager: JupyterZmqSocketManager
}

fun JupyterSocketBase.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}
