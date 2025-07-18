package org.jetbrains.kotlinx.jupyter.ws

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientReceiveSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig

class JupyterWsClientReceiveSocketManager(
    loggerFactory: KernelLoggerFactory,
) : JupyterClientReceiveSocketManager {
    private val delegate = JupyterWsClientSocketManager(loggerFactory)

    override fun open(config: KernelConfig): JupyterClientReceiveSockets = WsClientReceiveSockets(delegate.open(config))

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
