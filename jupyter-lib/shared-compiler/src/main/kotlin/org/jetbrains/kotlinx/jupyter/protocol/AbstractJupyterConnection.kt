package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessageCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.type

abstract class AbstractJupyterConnection : JupyterConnection {
    private val callbacks = mutableMapOf<RawMessageCallback, SocketRawMessageCallback>()

    abstract val socketManager: JupyterSocketManagerBase

    override fun addMessageCallback(callback: RawMessageCallback): RawMessageCallback {
        val socket = socketManager.fromSocketType(callback.socket)
        val socketCallback: SocketRawMessageCallback = { message ->
            if (callback.messageType?.let { it == message.type } != false) {
                callback.action(message)
            }
        }
        callbacks[callback] = socket.onRawMessage(socketCallback)
        return callback
    }

    override fun removeMessageCallback(callback: RawMessageCallback) {
        val socketCallback = callbacks[callback] ?: return
        val socket = socketManager.fromSocketType(callback.socket)
        socket.removeCallback(socketCallback)
    }

    override fun send(socketName: JupyterSocketType, message: RawMessage) {
        val socket = socketManager.fromSocketType(socketName)
        socket.sendRawMessage(message)
    }
}
