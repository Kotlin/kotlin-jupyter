package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.createZmqSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.SocketType
import org.zeromq.ZMQ

class JupyterZmqClientReceiveSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    private val side: JupyterSocketSide = JupyterSocketSide.CLIENT,
) : JupyterClientReceiveSocketManager {
    override fun open(config: KernelConfig): JupyterZmqClientReceiveSockets = JupyterZmqClientReceiveSockets(
        kernelConfig = config,
        loggerFactory = loggerFactory,
        side = side,
    )
}

class JupyterZmqClientReceiveSockets internal constructor(
    kernelConfig: KernelConfig,
    loggerFactory: KernelLoggerFactory,
    side: JupyterSocketSide,
) : JupyterClientReceiveSockets {
    val context: ZMQ.Context = ZMQ.context(/* ioThreads = */ 1)

    override val shell: JupyterZmqSocket
    override val control: JupyterZmqSocket
    override val ioPub: JupyterZmqSocket
    override val stdin: JupyterZmqSocket

    init {
        fun createSocket(info: JupyterZmqSocketInfo) =
            createZmqSocket(loggerFactory, info, context, kernelConfig, side)

        shell = createSocket(JupyterZmqSocketInfo.SHELL).apply {
            if (JupyterZmqSocketInfo.SHELL.zmqType(side) == SocketType.REQ) {
                zmqSocket.makeRelaxed()
            }
        }
        control = createSocket(JupyterZmqSocketInfo.CONTROL)
        ioPub = createSocket(JupyterZmqSocketInfo.IOPUB)
        stdin = createSocket(JupyterZmqSocketInfo.STDIN)

        mergeExceptions {
            catchIndependently {
                ioPub.zmqSocket.subscribe(byteArrayOf())
                shell.connect()
                ioPub.connect()
                stdin.connect()
                control.connect()
            }
            if (failing) {
                catchIndependently { close() }
            }
        }
    }

    override fun close() {
        mergeExceptions {
            for (socket in listOf(shell, control, ioPub, stdin)) {
                catchIndependently { socket.close() }
            }
            catchIndependently { context.term() }
        }
    }
}
