package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

interface MessageHandler {
    fun handleMessage(socketType: JupyterSocketType, message: RawMessage)
}
