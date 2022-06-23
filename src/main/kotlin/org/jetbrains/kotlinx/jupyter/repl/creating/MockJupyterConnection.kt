package org.jetbrains.kotlinx.jupyter.repl.creating

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocket
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.JupyterServerSocket
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import java.io.InputStream

object MockJupyterConnection : JupyterConnectionInternal {
    override val heartbeat: JupyterServerSocket
        get() = throw NotImplementedError()
    override val shell: JupyterServerSocket
        get() = throw NotImplementedError()
    override val control: JupyterServerSocket
        get() = throw NotImplementedError()
    override val stdin: JupyterServerSocket
        get() = throw NotImplementedError()
    override val iopub: JupyterServerSocket
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

    override fun sendStatus(status: KernelStatus, incomingMessage: Message?) {
        throw NotImplementedError()
    }

    override fun doWrappedInBusyIdle(incomingMessage: Message?, action: () -> Unit) {
        throw NotImplementedError()
    }

    override fun addMessageCallback(callback: RawMessageCallback): RawMessageCallback {
        throw NotImplementedError()
    }

    override fun removeMessageCallback(callback: RawMessageCallback) {
        throw NotImplementedError()
    }

    override fun send(socketName: JupyterSocket, message: RawMessage) {
        throw NotImplementedError()
    }

    override fun sendReply(socketName: JupyterSocket, parentMessage: RawMessage, type: String, content: JsonObject, metadata: JsonObject?) {
        throw NotImplementedError()
    }
}
