package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage

interface MessageHandler {
    fun handleMessage(
        socketType: JupyterSocketType,
        message: RawMessage,
    )
}
