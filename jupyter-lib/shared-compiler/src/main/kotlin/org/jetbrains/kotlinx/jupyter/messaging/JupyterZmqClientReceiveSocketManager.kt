package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.createZmqSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.ZMQ

class JupyterZmqClientReceiveSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    private val side: JupyterSocketSide = JupyterSocketSide.CLIENT,
) : JupyterClientReceiveSocketManager {
    override fun open(config: KernelConfig): JupyterClientReceiveSockets {
        return ZmqClientReceiveSockets(
            context = ZMQ.context(/* ioThreads = */ 1),
            kernelConfig = config,
            loggerFactory = loggerFactory,
            side = side,
        )
    }
}

private class ZmqClientReceiveSockets(
    private val context: ZMQ.Context,
    kernelConfig: KernelConfig,
    loggerFactory: KernelLoggerFactory,
    side: JupyterSocketSide,
) : JupyterClientReceiveSockets {

    override val shell = createZmqSocket(loggerFactory, JupyterZmqSocketInfo.SHELL, context, kernelConfig, side).apply {
        zmqSocket.makeRelaxed()
    }
    override val control = createZmqSocket(loggerFactory, JupyterZmqSocketInfo.CONTROL, context, kernelConfig, side)
    override val ioPub = createZmqSocket(loggerFactory, JupyterZmqSocketInfo.IOPUB, context, kernelConfig, side)
    override val stdin = createZmqSocket(loggerFactory, JupyterZmqSocketInfo.STDIN, context, kernelConfig, side)

    init {
        try {
            ioPub.zmqSocket.subscribe(byteArrayOf())
            shell.connect()
            ioPub.connect()
            stdin.connect()
            control.connect()
        } catch (e: Throwable) {
            try {
                close()
            } catch (_: NullPointerException) {
                // some sockets may not have been initialized at all
            }
            throw e
        }
    }

    override fun close() {
        shell.close()
        control.close()
        ioPub.close()
        stdin.close()
        context.term()
    }
}
