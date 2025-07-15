package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocketImpl
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.callbackBased
import org.jetbrains.kotlinx.jupyter.protocol.createZmqSocket
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.ZMQ
import java.io.Closeable

internal class JupyterZmqServerImplSockets(
    private val loggerFactory: KernelLoggerFactory,
    private val config: KernelConfig,
) : JupyterServerImplSockets,
    Closeable {
    private val logger = loggerFactory.getLogger(this::class)
    private val zmqContext: ZMQ.Context = ZMQ.context(1)

    private val socketsToClose = mutableListOf<Closeable>()
    private val socketsToBind = mutableListOf<JupyterZmqSocket>()

    private fun createSocket(socketInfo: JupyterZmqSocketInfo): JupyterZmqSocket =
        createZmqSocket(loggerFactory, socketInfo, zmqContext, config.jupyterParams, JupyterSocketSide.SERVER)
            .also { socketsToBind.add(it) }
            .also { socketsToClose.add(it) }

    private fun createCallbackBasedSocket(socketInfo: JupyterZmqSocketInfo): JupyterCallbackBasedSocketImpl =
        createZmqSocket(loggerFactory, socketInfo, zmqContext, config.jupyterParams, JupyterSocketSide.SERVER)
            .also { socketsToBind.add(it) }
            .callbackBased(logger)
            .also { socketsToClose.add(it) }

    val heartbeat = createSocket(JupyterZmqSocketInfo.HB)
    override val shell: JupyterCallbackBasedSocketImpl = createCallbackBasedSocket(JupyterZmqSocketInfo.SHELL)
    override val control: JupyterCallbackBasedSocketImpl = createCallbackBasedSocket(JupyterZmqSocketInfo.CONTROL)
    override val stdin = createSocket(JupyterZmqSocketInfo.STDIN)

    // we must initialize it last, so it will be the last to bind, this one is the slowest to bind
    // (see ZmqSocketWithCancellationImpl.bind implementation)
    override val iopub = createSocket(JupyterZmqSocketInfo.IOPUB)

    fun bindAll() {
        for (socket in socketsToBind) {
            socket.bind()
        }
    }

    override fun close() {
        mergeExceptions {
            for (socket in socketsToClose) {
                catchIndependently { socket.close() }
            }
            catchIndependently { zmqContext.close() }
        }
    }
}
