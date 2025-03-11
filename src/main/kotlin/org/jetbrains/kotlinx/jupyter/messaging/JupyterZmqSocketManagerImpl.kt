package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.zeromq.ZMQ
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JupyterZmqSocketManagerImpl(
    private val terminationTimeout: Duration = 15.seconds,
    private val openSocketAction: (JupyterZmqSocketInfo, ZMQ.Context) -> JupyterZmqSocket,
) : JupyterZmqSocketManager {
    private val zmqContext: ZMQ.Context = ZMQ.context(1)

    private fun openSocket(socketInfo: JupyterZmqSocketInfo) = openSocketAction(socketInfo, zmqContext)

    override val heartbeat = openSocket(JupyterZmqSocketInfo.HB)
    override val shell = openSocket(JupyterZmqSocketInfo.SHELL)
    override val control = openSocket(JupyterZmqSocketInfo.CONTROL)
    override val stdin = openSocket(JupyterZmqSocketInfo.STDIN)
    override val iopub = openSocket(JupyterZmqSocketInfo.IOPUB)

    private val socketsMap =
        buildMap {
            put(JupyterSocketType.HB, heartbeat)
            put(JupyterSocketType.SHELL, shell)
            put(JupyterSocketType.CONTROL, control)
            put(JupyterSocketType.STDIN, stdin)
            put(JupyterSocketType.IOPUB, iopub)
        }

    override fun fromSocketType(type: JupyterSocketType): JupyterZmqSocket =
        socketsMap[type] ?: throw RuntimeException("Unknown socket type: $type")

    override fun close() {
        closeWithTimeout(terminationTimeout.inWholeMilliseconds, ::doClose)
    }

    private fun doClose() {
        socketsMap.values.forEach { it.close() }
        zmqContext.close()
    }
}
