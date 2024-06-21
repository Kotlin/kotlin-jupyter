package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.receiveMessage
import org.jetbrains.kotlinx.jupyter.util.DefaultPromptOptions
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
    val message =
        messageFactory.makeReplyMessageOrNull(
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

fun JupyterCommunicationFacility.sendSimpleMessageToIoPub(
    msgType: MessageType,
    content: AbstractMessageContent,
) {
    socketManager.iopub.sendMessage(messageFactory.makeSimpleMessage(msgType, content))
}

fun JupyterCommunicationFacility.sendWrapped(message: Message) =
    doWrappedInBusyIdle {
        socketManager.shell.sendMessage(message)
    }

fun JupyterCommunicationFacility.sendOut(
    stream: JupyterOutType,
    text: String,
) {
    socketManager.iopub.sendMessage(
        messageFactory.makeReplyMessage(msgType = MessageType.STREAM, content = StreamResponse(stream.optionName(), text)),
    )
}

/**
 * Send a message to clients of the type "error" as the response
 * to an "execute_request" message that resulted in the REPL throwing
 * an exception.
 */
fun JupyterCommunicationFacility.sendError(
    response: JupyterResponse,
    executionCount: ExecutionCount,
    startedTime: String,
) {
    val replyContent: MessageReplyContent =
        when (val ex = response.exception!!) {
            is ReplEvalRuntimeException -> ex.toExecuteErrorReply(executionCount)
            else -> ex.toExecuteErrorReply(executionCount)
        }
    val replyMetadata =
        ExecuteReplyMetadata(
            true,
            messageFactory.sessionId,
            response.status,
            startedTime,
            response.metadata,
        )

    val reply =
        messageFactory.makeReplyMessage(
            MessageType.ERROR,
            content = replyContent,
            metadata = MessageFormat.encodeToJsonElement(replyMetadata),
        )

    System.err.println("Sending error: $reply")
    socketManager.iopub.sendMessage(reply)
}

fun JupyterCommunicationFacility.getInput(
    prompt: String = DefaultPromptOptions.PROMPT,
    password: Boolean = DefaultPromptOptions.IS_PASSWORD,
): String {
    val stdinSocket = socketManager.stdin
    val request = InputRequest(prompt, password)
    stdinSocket.sendMessage(
        messageFactory.makeReplyMessage(MessageType.INPUT_REQUEST, content = request),
    )
    val msg = stdinSocket.receiveMessage()
    val content = msg?.data?.content as? InputReply

    return content?.value ?: throw UnsupportedOperationException("Unexpected input message $msg")
}
