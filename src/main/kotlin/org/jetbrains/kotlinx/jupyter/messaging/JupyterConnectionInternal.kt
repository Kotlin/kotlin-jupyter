package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketManagerBase
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.Closeable
import java.io.InputStream

interface MessageFactory {
    val messageId: List<ByteArray>
    val sessionId: String
    val username: String
    val contextMessage: RawMessage?

    fun updateSessionInfo(message: RawMessage)
    fun updateContextMessage(contextMessage: RawMessage?)
}

interface JupyterSocketManager : JupyterSocketManagerBase, Closeable {
    val heartbeat: JupyterSocket
    val shell: JupyterSocket
    val control: JupyterSocket
    val stdin: JupyterSocket
    val iopub: JupyterSocket
}

interface JupyterConnectionInternal : JupyterConnection {
    val config: KernelConfig
    val socketManager: JupyterSocketManager
    val messageFactory: MessageFactory

    val executor: JupyterExecutor
    val stdinIn: InputStream
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

fun JupyterSocket.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}

fun JupyterConnectionInternal.sendOut(msg: RawMessage, stream: JupyterOutType, text: String) {
    socketManager.iopub.sendMessage(makeReplyMessage(msg, header = makeHeader(MessageType.STREAM, msg), content = StreamResponse(stream.optionName(), text)))
}

fun JupyterConnectionInternal.sendSimpleMessageToIoPub(msgType: MessageType, content: MessageContent) {
    socketManager.iopub.sendMessage(messageFactory.makeSimpleMessage(msgType, content))
}

fun JupyterConnectionInternal.sendStatus(status: KernelStatus, incomingMessage: RawMessage?) {
    val message = if (incomingMessage != null) makeReplyMessage(
        incomingMessage,
        MessageType.STATUS,
        content = StatusReply(status),
    )
    else messageFactory.makeSimpleMessage(MessageType.STATUS, content = StatusReply(status))
    socketManager.iopub.sendMessage(message)
}

fun JupyterConnectionInternal.doWrappedInBusyIdle(incomingMessage: RawMessage?, action: () -> Unit) {
    sendStatus(KernelStatus.BUSY, incomingMessage)
    try {
        action()
    } finally {
        sendStatus(KernelStatus.IDLE, incomingMessage)
    }
}
