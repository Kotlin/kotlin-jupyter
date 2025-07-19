package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.zeromq.SocketType
import org.zeromq.ZMQ

class JupyterZmqClientReceiveSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    private val side: JupyterSocketSide = JupyterSocketSide.CLIENT,
) : JupyterClientReceiveSocketManager {
    override fun open(configParams: KernelJupyterParams): JupyterZmqClientReceiveSockets =
        JupyterZmqClientReceiveSockets(
            configParams = configParams,
            loggerFactory = loggerFactory,
            side = side,
        )
}

class JupyterZmqClientReceiveSockets internal constructor(
    configParams: KernelJupyterParams,
    loggerFactory: KernelLoggerFactory,
    side: JupyterSocketSide,
) : JupyterClientReceiveSockets {
    val context: ZMQ.Context = ZMQ.context(/* ioThreads = */ 1)
    private val hmac by lazy { configParams.createHmac() }

    override val shell: JupyterZmqSocket
    override val control: JupyterZmqSocket
    override val ioPub: JupyterZmqSocket
    override val stdin: JupyterZmqSocket

    init {
        fun createSocket(info: JupyterZmqSocketInfo) = createZmqSocket(loggerFactory, info, context, configParams, side, hmac)

        shell =
            createSocket(JupyterZmqSocketInfo.SHELL).apply {
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
