package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonElement
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.ZmqIdentities

interface MessageFactory {
    val zmqIdentities: ZmqIdentities
    val sessionId: String
    val username: String
    val contextMessage: RawMessage?

    fun updateSessionInfo(message: RawMessage)

    fun updateContextMessage(contextMessage: RawMessage?)

    fun makeReplyMessageOrNull(
        msgType: MessageType? = null,
        sessionId: String? = null,
        header: MessageHeader? = null,
        parentHeader: MessageHeader? = null,
        metadata: JsonElement? = null,
        content: MessageContent? = null,
        buffers: List<ByteArray>? = null,
    ): Message?
}

fun MessageFactory.makeDefaultHeader(msgType: MessageType): MessageHeader = makeHeader(msgType, sessionId = sessionId, username = username)

fun MessageFactory.makeSimpleMessage(
    msgType: MessageType,
    content: MessageContent,
    metadata: JsonElement? = null,
    buffers: List<ByteArray>? = null,
): Message =
    Message(
        zmqIdentities = zmqIdentities,
        data =
            MessageData(
                header = makeDefaultHeader(msgType),
                content = content,
                metadata = metadata,
            ),
        buffers = buffers ?: emptyList(),
    )

fun MessageFactory.makeReplyMessage(
    msgType: MessageType? = null,
    sessionId: String? = null,
    header: MessageHeader? = null,
    parentHeader: MessageHeader? = null,
    metadata: JsonElement? = null,
    content: MessageContent? = null,
    buffers: List<ByteArray>? = null,
): Message =
    makeReplyMessageOrNull(
        msgType,
        sessionId,
        header,
        parentHeader,
        metadata,
        content,
        buffers,
    ) ?: error("Context message is needed for reply")
