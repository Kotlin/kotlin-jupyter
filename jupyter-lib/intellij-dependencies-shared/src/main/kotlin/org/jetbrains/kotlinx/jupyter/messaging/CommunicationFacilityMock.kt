package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets

object CommunicationFacilityMock : JupyterCommunicationFacility {
    override val messageFactory: MessageFactory
        get() = throw NotImplementedError()

    override val socketManager: JupyterServerSockets
        get() = throw NotImplementedError()
}
