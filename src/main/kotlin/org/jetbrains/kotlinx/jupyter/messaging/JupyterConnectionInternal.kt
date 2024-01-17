package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketManagerBase
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.Closeable
import java.io.InputStream

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
