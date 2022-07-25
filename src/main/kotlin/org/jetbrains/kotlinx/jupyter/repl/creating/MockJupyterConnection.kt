package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.InputStream

object MockJupyterConnection : JupyterConnectionInternal {
    override val config: KernelConfig
        get() = throw NotImplementedError()
    override val contextMessage: RawMessage
        get() = throw NotImplementedError()
    override val heartbeat: JupyterSocket
        get() = throw NotImplementedError()
    override val shell: JupyterSocket
        get() = throw NotImplementedError()
    override val control: JupyterSocket
        get() = throw NotImplementedError()
    override val stdin: JupyterSocket
        get() = throw NotImplementedError()
    override val iopub: JupyterSocket
        get() = throw NotImplementedError()
    override val messageId: List<ByteArray>
        get() = throw NotImplementedError()
    override val sessionId: String
        get() = throw NotImplementedError()
    override val username: String
        get() = throw NotImplementedError()
    override val executor: JupyterExecutor
        get() = throw NotImplementedError()
    override val stdinIn: InputStream
        get() = throw NotImplementedError()

    override fun sendStatus(status: KernelStatus, incomingMessage: RawMessage?) {
        throw NotImplementedError()
    }

    override fun doWrappedInBusyIdle(incomingMessage: RawMessage?, action: () -> Unit) {
        throw NotImplementedError()
    }

    override fun updateSessionInfo(message: RawMessage) {
        throw NotImplementedError()
    }

    override fun setContextMessage(message: RawMessage?) {
        throw NotImplementedError()
    }

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
