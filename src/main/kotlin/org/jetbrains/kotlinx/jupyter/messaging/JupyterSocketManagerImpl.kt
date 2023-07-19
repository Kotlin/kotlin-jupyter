package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.zeromq.ZMQ

class JupyterSocketManagerImpl(
    private val openSocketAction: (JupyterSocketInfo, ZMQ.Context) -> JupyterSocket,
) : JupyterSocketManager {
    private val zmqContext: ZMQ.Context = ZMQ.context(1)
    private fun openSocket(socketInfo: JupyterSocketInfo) = openSocketAction(socketInfo, zmqContext)

    override val heartbeat = openSocket(JupyterSocketInfo.HB)
    override val shell = openSocket(JupyterSocketInfo.SHELL)
    override val control = openSocket(JupyterSocketInfo.CONTROL)
    override val stdin = openSocket(JupyterSocketInfo.STDIN)
    override val iopub = openSocket(JupyterSocketInfo.IOPUB)

    private val socketsMap = buildMap {
        put(JupyterSocketType.HB, heartbeat)
        put(JupyterSocketType.SHELL, shell)
        put(JupyterSocketType.CONTROL, control)
        put(JupyterSocketType.STDIN, stdin)
        put(JupyterSocketType.IOPUB, iopub)
    }

    override fun fromSocketType(type: JupyterSocketType): JupyterSocket = socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    override fun close() {
        socketsMap.values.forEach { it.close() }
        zmqContext.close()
    }
}
