package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketManagerBase
import java.io.Closeable

interface JupyterBaseSockets {
    val heartbeat: JupyterSocketBase
    val shell: JupyterSocketBase
    val control: JupyterSocketBase
    val stdin: JupyterSocketBase
    val iopub: JupyterSocketBase
}

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
