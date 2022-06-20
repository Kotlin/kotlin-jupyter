package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

interface JupyterConnectionInternal : JupyterConnection {
    val heartbeat: JupyterServerSocket
    val shell: JupyterServerSocket
    val control: JupyterServerSocket
    val stdin: JupyterServerSocket
    val iopub: JupyterServerSocket

    val messageId: List<ByteArray>
    val sessionId: String
    val username: String
}

fun JupyterConnectionInternal.makeDefaultHeader(msgType: MessageType): MessageHeader {
    return makeHeader(msgType, sessionId = sessionId, username = username)
}

fun JupyterConnectionInternal.makeSimpleMessage(msgType: MessageType, content: MessageContent): Message {
    return Message(
        id = messageId,
        data = MessageData(
            header = makeDefaultHeader(msgType),
            content = content
        )
    )
}

interface JupyterServerSocket {
    val connection: JupyterConnectionInternal

    fun sendRawMessage(msg: RawMessage)
}

fun JupyterServerSocket.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}

fun JupyterServerSocket.sendOut(msg: Message, stream: JupyterOutType, text: String) {
    sendMessage(makeReplyMessage(msg, header = makeHeader(MessageType.STREAM, msg), content = StreamResponse(stream.optionName(), text)))
}

fun JupyterServerSocket.sendSimpleMessage(msgType: MessageType, content: MessageContent) {
    sendMessage(connection.makeSimpleMessage(msgType, content))
}
