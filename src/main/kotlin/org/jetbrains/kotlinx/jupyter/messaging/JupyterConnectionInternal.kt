package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.InputStream

interface JupyterConnectionInternal : JupyterConnection {
    val config: KernelConfig
    val contextMessage: RawMessage?

    val heartbeat: JupyterSocket
    val shell: JupyterSocket
    val control: JupyterSocket
    val stdin: JupyterSocket
    val iopub: JupyterSocket

    val messageId: List<ByteArray>
    val sessionId: String
    val username: String

    val executor: JupyterExecutor
    val stdinIn: InputStream

    val debugPort: Int?

    fun sendStatus(status: KernelStatus, incomingMessage: RawMessage? = null)
    fun doWrappedInBusyIdle(incomingMessage: RawMessage? = null, action: () -> Unit)
    fun updateSessionInfo(message: RawMessage)
    fun setContextMessage(message: RawMessage?)
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

fun JupyterSocket.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}

fun JupyterConnectionInternal.sendOut(msg: RawMessage, stream: JupyterOutType, text: String) {
    iopub.sendMessage(makeReplyMessage(msg, header = makeHeader(MessageType.STREAM, msg), content = StreamResponse(stream.optionName(), text)))
}

fun JupyterConnectionInternal.sendSimpleMessageToIoPub(msgType: MessageType, content: MessageContent) {
    iopub.sendMessage(makeSimpleMessage(msgType, content))
}
