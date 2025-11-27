package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.zeromq.ZMQ

// Used on the IDE side
@Suppress("unused")
class JupyterZmqClientSocketManager(
    private val loggerFactory: KernelLoggerFactory,
) : JupyterClientSocketManager {
    override fun open(configParams: KernelJupyterParams): JupyterZmqClientSockets =
        JupyterZmqClientSockets(
            configParams = configParams,
            loggerFactory = loggerFactory,
        )
}

class JupyterZmqClientSockets internal constructor(
    configParams: KernelJupyterParams,
    loggerFactory: KernelLoggerFactory,
) : JupyterClientSockets {
    val context: ZMQ.Context = ZMQ.context(/* ioThreads = */ 1)
    private val hmac by lazy { configParams.createHmac() }
    private val identity: ByteArray = generateZmqIdentity()

    override val shell: JupyterZmqSocket
    override val control: JupyterZmqSocket
    override val ioPub: JupyterZmqSocket
    override val stdin: JupyterZmqSocket

    init {
        fun createSocket(info: JupyterZmqSocketInfo) =
            createZmqSocket(
                loggerFactory,
                info,
                context,
                configParams,
                JupyterSocketSide.CLIENT,
                hmac,
                identity,
            )

        shell = createSocket(JupyterZmqSocketInfo.SHELL)
        control = createSocket(JupyterZmqSocketInfo.CONTROL)
        ioPub = createSocket(JupyterZmqSocketInfo.IOPUB)
        stdin = createSocket(JupyterZmqSocketInfo.STDIN)

        mergeExceptions {
            catchIndependently {
                for (socket in listOf(ioPub, shell, stdin, control)) {
                    socket.connect()
                }
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
