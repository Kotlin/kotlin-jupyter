package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactory
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.InputStream

object MockJupyterConnection : JupyterConnectionInternal {
    override val config: KernelConfig
        get() = throw NotImplementedError()
    override val socketManager: JupyterSocketManager
        get() = throw NotImplementedError()
    override val messageFactory: MessageFactory
        get() = throw NotImplementedError()

    override val executor: JupyterExecutor
        get() = throw NotImplementedError()
    override val stdinIn: InputStream
        get() = throw NotImplementedError()

    override fun addMessageCallback(callback: RawMessageCallback): RawMessageCallback {
        throw NotImplementedError()
    }

    override fun removeMessageCallback(callback: RawMessageCallback) {
        throw NotImplementedError()
    }

    override fun send(socketName: JupyterSocketType, message: RawMessage) {
        throw NotImplementedError()
    }
}
