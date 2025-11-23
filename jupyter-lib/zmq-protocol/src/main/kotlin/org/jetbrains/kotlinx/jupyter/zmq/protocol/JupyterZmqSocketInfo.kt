package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.zeromq.SocketType

/**
 * Defines the ZMQ socket types for the kernel (server) and various clients,
 *
 * These values must match the Jupyter Messaging Protocol specification.
 *
 * @see <a href="https://jupyter-client.readthedocs.io/en/stable/messaging.html#sockets">Jupyter Messaging: Sockets</a>
 */
enum class JupyterZmqSocketInfo(
    val type: JupyterSocketType,
    val zmqKernelType: SocketType,
    val zmqClientType: SocketType,
) {
    HB(JupyterSocketType.HB, SocketType.ROUTER, SocketType.DEALER),
    SHELL(JupyterSocketType.SHELL, SocketType.ROUTER, SocketType.DEALER),
    CONTROL(JupyterSocketType.CONTROL, SocketType.ROUTER, SocketType.DEALER),
    STDIN(JupyterSocketType.STDIN, SocketType.ROUTER, SocketType.DEALER),
    IOPUB(JupyterSocketType.IOPUB, SocketType.PUB, SocketType.SUB),
    ;

    fun zmqType(side: JupyterSocketSide): SocketType =
        when (side) {
            JupyterSocketSide.SERVER -> zmqKernelType
            JupyterSocketSide.CLIENT -> zmqClientType
        }
}
