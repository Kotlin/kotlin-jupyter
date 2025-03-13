package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.AbstractJupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.openServerZmqSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig

class JupyterZmqConnectionImpl(
    private val loggerFactory: KernelLoggerFactory,
    private val config: KernelConfig,
) : AbstractJupyterConnection(), JupyterZmqConnectionInternal {
    override val socketManager: JupyterZmqSocketManager =
        JupyterZmqSocketManagerImpl { socketInfo, context ->
            openServerZmqSocket(
                loggerFactory,
                socketInfo,
                context,
                config,
            )
        }

    override fun close() {
        socketManager.close()
    }
}
