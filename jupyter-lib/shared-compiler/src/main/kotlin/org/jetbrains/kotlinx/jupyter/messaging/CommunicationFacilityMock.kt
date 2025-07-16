package org.jetbrains.kotlinx.jupyter.messaging

object CommunicationFacilityMock : JupyterCommunicationFacility {
    override val messageFactory: MessageFactory
        get() = throw NotImplementedError()

    override val socketManager: JupyterServerSockets
        get() = throw NotImplementedError()
}
