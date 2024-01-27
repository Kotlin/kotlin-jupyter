package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.AbstractJupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.openServerSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.Closeable

class JupyterConnectionImpl(
    private val config: KernelConfig,
) : AbstractJupyterConnection(), JupyterConnectionInternal, Closeable {

    override val socketManager: JupyterSocketManager = JupyterSocketManagerImpl { socketInfo, context ->
        openServerSocket(
            socketInfo,
            context,
            config,
        )
    }

    override fun close() {
        socketManager.close()
    }
}
