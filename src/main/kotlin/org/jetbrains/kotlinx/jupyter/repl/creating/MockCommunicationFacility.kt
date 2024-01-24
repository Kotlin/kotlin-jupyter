package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactory

object MockCommunicationFacility : JupyterCommunicationFacility {
    override val messageFactory: MessageFactory
        get() = throw NotImplementedError()

    override val socketManager: JupyterSocketManager
        get() = throw NotImplementedError()
}
