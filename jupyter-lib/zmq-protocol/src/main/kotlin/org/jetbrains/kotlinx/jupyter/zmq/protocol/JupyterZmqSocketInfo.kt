package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.zeromq.SocketType

enum class JupyterZmqSocketInfo(
    val type: JupyterSocketType,
    val zmqKernelType: SocketType,
    val zmqClientType: SocketType,
    val zmqIdeClientType: SocketType,
) {
    HB(JupyterSocketType.HB, SocketType.REP, SocketType.REQ, SocketType.DEALER),
    SHELL(JupyterSocketType.SHELL, SocketType.ROUTER, SocketType.DEALER, SocketType.DEALER),
    CONTROL(JupyterSocketType.CONTROL, SocketType.ROUTER, SocketType.DEALER, SocketType.DEALER),
    STDIN(JupyterSocketType.STDIN, SocketType.ROUTER, SocketType.DEALER, SocketType.DEALER),
    IOPUB(JupyterSocketType.IOPUB, SocketType.PUB, SocketType.SUB, SocketType.SUB),
    ;

    fun zmqType(side: JupyterSocketSide): SocketType =
        when (side) {
            JupyterSocketSide.SERVER -> zmqKernelType
            JupyterSocketSide.CLIENT -> zmqClientType
            JupyterSocketSide.IDE_CLIENT -> zmqIdeClientType
        }
}
