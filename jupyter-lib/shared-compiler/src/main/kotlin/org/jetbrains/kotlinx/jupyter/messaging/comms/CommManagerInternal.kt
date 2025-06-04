package org.jetbrains.kotlinx.jupyter.messaging.comms

import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.messaging.CommCloseMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommMsgMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommOpenMessage
import org.jetbrains.kotlinx.jupyter.messaging.Message

interface CommManagerInternal : CommManager {
    fun processCommOpen(
        message: Message,
        content: CommOpenMessage,
    ): Comm?

    fun processCommMessage(
        message: Message,
        content: CommMsgMessage,
    )

    fun processCommClose(
        message: Message,
        content: CommCloseMessage,
    )
}
