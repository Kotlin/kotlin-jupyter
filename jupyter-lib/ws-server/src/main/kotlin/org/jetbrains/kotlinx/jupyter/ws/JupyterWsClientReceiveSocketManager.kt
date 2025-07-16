package org.jetbrains.kotlinx.jupyter.ws

import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSockets
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams

class JupyterWsClientReceiveSocketManager(
    loggerFactory: KernelLoggerFactory,
) : JupyterClientReceiveSocketManager {
    private val delegate = JupyterWsClientSocketManager(loggerFactory)

    override fun open(configParams: KernelJupyterParams): JupyterClientReceiveSockets = WsClientReceiveSockets(delegate.open(configParams))

    private class WsClientReceiveSockets(
        private val delegate: JupyterClientSockets,
    ) : JupyterClientReceiveSockets {
        override val shell: JupyterSendReceiveSocket = delegate.shell.sendReceive()
        override val control: JupyterSendReceiveSocket = delegate.control.sendReceive()
        override val ioPub: JupyterReceiveSocket = delegate.ioPub.sendReceive()
        override val stdin: JupyterSendReceiveSocket = delegate.stdin.sendReceive()

        override fun close() = delegate.close()
    }
}
