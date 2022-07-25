package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.zeromq.SocketType

enum class JupyterSocketInfo(val type: JupyterSocketType, val zmqKernelType: SocketType, val zmqClientType: SocketType) {
    HB(JupyterSocketType.HB, SocketType.REP, SocketType.REQ),
    SHELL(JupyterSocketType.SHELL, SocketType.ROUTER, SocketType.REQ),
    CONTROL(JupyterSocketType.CONTROL, SocketType.ROUTER, SocketType.REQ),
    STDIN(JupyterSocketType.STDIN, SocketType.ROUTER, SocketType.REQ),
    IOPUB(JupyterSocketType.IOPUB, SocketType.PUB, SocketType.SUB);

    fun zmqType(side: JupyterSocketSide): SocketType = when (side) {
        JupyterSocketSide.SERVER -> zmqKernelType
        JupyterSocketSide.CLIENT -> zmqClientType
    }
}
