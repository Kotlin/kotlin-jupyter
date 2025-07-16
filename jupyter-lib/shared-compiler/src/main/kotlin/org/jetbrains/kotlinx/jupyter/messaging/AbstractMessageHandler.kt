package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage

abstract class AbstractMessageHandler : MessageHandler {
    abstract fun createProcessor(message: RawMessage): MessageRequestProcessor

    override fun handleMessage(
        socketType: JupyterSocketType,
        message: RawMessage,
    ) {
        val processor = createProcessor(message)
        when (socketType) {
            JupyterSocketType.SHELL -> processor.processShellMessage()
            JupyterSocketType.CONTROL -> processor.processControlMessage()
            JupyterSocketType.STDIN -> processor.processStdinMessage()
            else -> {}
        }
    }
}
