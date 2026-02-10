package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.ZmqIdentities

class MessageFactoryImpl : MessageFactory {
    private var _zmqIdentities: ZmqIdentities = listOf(byteArrayOf(1))
    override val zmqIdentities: ZmqIdentities
        get() = _zmqIdentities

    private var _sessionId = ""
    override val sessionId: String
        get() = _sessionId

    private var _username = ""
    override val username: String
        get() = _username

    private var _contextMessage: RawMessage? = null
    override val contextMessage: RawMessage?
        get() = _contextMessage

    override fun updateSessionInfo(message: RawMessage) {
        val header = message.header
        header["session"]?.jsonPrimitive?.content?.let { _sessionId = it }
        header["username"]?.jsonPrimitive?.content?.let { _username = it }
        _zmqIdentities = message.zmqIdentities
    }

    override fun updateContextMessage(contextMessage: RawMessage?) {
        _contextMessage = contextMessage
    }

    override fun makeReplyMessageOrNull(
        msgType: MessageType?,
        sessionId: String?,
        header: MessageHeader?,
        parentHeader: MessageHeader?,
        metadata: JsonElement?,
        content: MessageContent?,
        buffers: List<ByteArray>?,
    ): Message? {
        val myContextMessage = contextMessage ?: return null

        return makeReplyMessage(
            myContextMessage,
            msgType,
            sessionId,
            header,
            parentHeader,
            metadata,
            content,
            buffers,
        )
    }
}
