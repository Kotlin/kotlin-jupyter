package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

interface MessageRequestProcessor {
    fun processShellMessage(message: RawMessage)
    fun processControlMessage(message: RawMessage)
}
