package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.zeromq.ZMQ

class JupyterZmqClientReceiveSocketManager(
    private val loggerFactory: KernelLoggerFactory,
) : JupyterClientReceiveSocketManager {
    override fun open(configParams: KernelJupyterParams): JupyterZmqClientReceiveSockets =
        JupyterZmqClientReceiveSockets(
            configParams = configParams,
            loggerFactory = loggerFactory,
        )
}

class JupyterZmqClientReceiveSockets internal constructor(
    configParams: KernelJupyterParams,
    loggerFactory: KernelLoggerFactory,
) : JupyterClientReceiveSockets {
    private val delegate = JupyterZmqClientSockets(configParams, loggerFactory)
    val context: ZMQ.Context get() = delegate.context

    override val shell = delegate.shell.zmqSendReceive()
    override val control = delegate.control.zmqSendReceive()
    override val ioPub = delegate.ioPub.zmqSendReceive()
    override val stdin = delegate.stdin.zmqSendReceive()

    // Socket wrappers contain only byte arrays, so they don't need a specific cleanup
    override fun close() = delegate.close()
}
