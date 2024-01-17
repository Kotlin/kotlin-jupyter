package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

interface MessageFactory {
    val messageId: List<ByteArray>
    val sessionId: String
    val username: String
    val contextMessage: RawMessage?

    fun updateSessionInfo(message: RawMessage)
    fun updateContextMessage(contextMessage: RawMessage?)
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

fun MessageFactory.makeReplyMessage(msgType: MessageType, content: MessageContent): Message {
    return makeReplyMessage(contextMessage!!, msgType, content = content)
}
