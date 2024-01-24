package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonElement
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

interface MessageFactory {
    val messageId: List<ByteArray>
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
    ): Message?
}

fun MessageFactory.makeDefaultHeader(msgType: MessageType): MessageHeader {
    return makeHeader(msgType, sessionId = sessionId, username = username)
}

fun MessageFactory.makeSimpleMessage(msgType: MessageType, content: MessageContent): Message {
    return Message(
        id = messageId,
        data = MessageData(
            header = makeDefaultHeader(msgType),
            content = content,
        ),
    )
}

fun MessageFactory.makeReplyMessage(
    msgType: MessageType? = null,
    sessionId: String? = null,
    header: MessageHeader? = null,
    parentHeader: MessageHeader? = null,
    metadata: JsonElement? = null,
    content: MessageContent? = null,
): Message {
    return makeReplyMessageOrNull(
        msgType,
        sessionId,
        header,
        parentHeader,
        metadata,
        content,
    ) ?: error("Context message is needed for reply")
}
