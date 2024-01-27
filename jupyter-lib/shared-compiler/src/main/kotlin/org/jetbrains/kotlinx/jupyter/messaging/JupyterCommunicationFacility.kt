package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.util.Provider

interface JupyterCommunicationFacility {
    val socketManager: JupyterBaseSockets
    val messageFactory: MessageFactory
}

class JupyterCommunicationFacilityImpl(
    override val socketManager: JupyterBaseSockets,
    private val messageFactoryProvider: Provider<MessageFactory>,
) : JupyterCommunicationFacility {
    override val messageFactory: MessageFactory
        get() = messageFactoryProvider.provide() ?: throw ReplException("No context message provided")
}

fun JupyterCommunicationFacility.sendStatus(status: KernelStatus) {
    val message = messageFactory.makeReplyMessageOrNull(
        MessageType.STATUS,
        content = StatusReply(status),
    ) ?: messageFactory.makeSimpleMessage(MessageType.STATUS, content = StatusReply(status))
    socketManager.iopub.sendMessage(message)
}

fun JupyterCommunicationFacility.doWrappedInBusyIdle(action: () -> Unit) {
    sendStatus(KernelStatus.BUSY)
    try {
        action()
    } finally {
        sendStatus(KernelStatus.IDLE)
    }
}

fun JupyterCommunicationFacility.sendSimpleMessageToIoPub(msgType: MessageType, content: MessageContent) {
    socketManager.iopub.sendMessage(messageFactory.makeSimpleMessage(msgType, content))
}

fun JupyterCommunicationFacility.sendWrapped(message: Message) = doWrappedInBusyIdle {
    socketManager.shell.sendMessage(message)
}

fun JupyterCommunicationFacility.sendOut(stream: JupyterOutType, text: String) {
    socketManager.iopub.sendMessage(
        messageFactory.makeReplyMessage(msgType = MessageType.STREAM, content = StreamResponse(stream.optionName(), text)),
    )
}

fun JupyterCommunicationFacility.sendResponse(response: Response, requestCount: Long, startedTime: String) {
    val stdOut = response.stdOut
    if (!stdOut.isNullOrEmpty()) {
        sendOut(JupyterOutType.STDOUT, stdOut)
    }

    val stdErr = response.stdErr
    if (!stdErr.isNullOrEmpty()) {
        sendOut(JupyterOutType.STDERR, stdErr)
    }
    response.sendBody(socketManager, requestCount, messageFactory, startedTime)
}
