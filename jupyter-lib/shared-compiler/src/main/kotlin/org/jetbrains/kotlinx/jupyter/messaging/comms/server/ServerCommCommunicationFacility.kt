package org.jetbrains.kotlinx.jupyter.messaging.comms.server

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.messaging.CommCloseMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommMsgMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommOpenMessage
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.doWrappedInBusyIdle
import org.jetbrains.kotlinx.jupyter.messaging.sendSimpleMessageToIoPub
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.comms.CommCommunicationFacility

class ServerCommCommunicationFacility(
    private val communicationFacility: JupyterCommunicationFacility,
) : CommCommunicationFacility {
    override val contextMessage: RawMessage?
        get() = communicationFacility.messageFactory.contextMessage

    override fun sendCommOpen(
        commId: String,
        targetName: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ) {
        communicationFacility.sendSimpleMessageToIoPub(
            msgType = MessageType.COMM_OPEN,
            content = CommOpenMessage(commId, targetName, data),
            metadata = metadata,
            buffers = buffers,
        )
    }

    override fun sendCommMessage(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ) {
        communicationFacility.sendSimpleMessageToIoPub(
            msgType = MessageType.COMM_MSG,
            content = CommMsgMessage(commId, data),
            metadata = metadata,
            buffers = buffers,
        )
    }

    override fun sendCommClose(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ) {
        communicationFacility.sendSimpleMessageToIoPub(
            msgType = MessageType.COMM_CLOSE,
            content = CommCloseMessage(commId, data),
            metadata = metadata,
            buffers = buffers,
        )
    }

    override fun processCallbacks(action: () -> Unit) {
        communicationFacility.doWrappedInBusyIdle(action)
    }
}
