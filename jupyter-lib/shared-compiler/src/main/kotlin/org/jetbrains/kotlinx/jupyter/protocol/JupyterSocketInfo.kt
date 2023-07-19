package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.zeromq.SocketType

enum class JupyterSocketInfo(
    val type: JupyterSocketType,
    val zmqKernelType: SocketType,
    val zmqClientType: SocketType,
    val zmqIdeClientType: SocketType,
) {
    HB(JupyterSocketType.HB, SocketType.REP, SocketType.REQ, SocketType.DEALER),
    SHELL(JupyterSocketType.SHELL, SocketType.ROUTER, SocketType.REQ, SocketType.DEALER),
    CONTROL(JupyterSocketType.CONTROL, SocketType.ROUTER, SocketType.REQ, SocketType.DEALER),
    STDIN(JupyterSocketType.STDIN, SocketType.ROUTER, SocketType.REQ, SocketType.DEALER),
    IOPUB(JupyterSocketType.IOPUB, SocketType.PUB, SocketType.SUB, SocketType.SUB);

    fun zmqType(side: JupyterSocketSide): SocketType = when (side) {
        JupyterSocketSide.SERVER -> zmqKernelType
        JupyterSocketSide.CLIENT -> zmqClientType
        JupyterSocketSide.IDE_CLIENT -> zmqIdeClientType
    }
}
