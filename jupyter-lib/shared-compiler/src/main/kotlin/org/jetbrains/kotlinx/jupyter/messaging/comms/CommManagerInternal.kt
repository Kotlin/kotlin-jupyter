package org.jetbrains.kotlinx.jupyter.messaging.comms

import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.messaging.CommClose
import org.jetbrains.kotlinx.jupyter.messaging.CommMsg
import org.jetbrains.kotlinx.jupyter.messaging.CommOpen
import org.jetbrains.kotlinx.jupyter.messaging.Message

interface CommManagerInternal : CommManager {
    fun processCommOpen(message: Message, content: CommOpen): Comm?
    fun processCommMessage(message: Message, content: CommMsg)
    fun processCommClose(message: Message, content: CommClose)
}
