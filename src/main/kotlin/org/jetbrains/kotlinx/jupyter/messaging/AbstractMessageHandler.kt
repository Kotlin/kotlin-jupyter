package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

abstract class AbstractMessageHandler : MessageHandler {
    abstract fun createProcessor(message: RawMessage): MessageRequestProcessor

    override fun handleMessage(socketType: JupyterSocketType, message: RawMessage) {
        val processor = createProcessor(message)
        when (socketType) {
            JupyterSocketType.SHELL -> processor.processShellMessage(message)
            JupyterSocketType.CONTROL -> processor.processControlMessage(message)
            else -> {}
        }
    }
}
