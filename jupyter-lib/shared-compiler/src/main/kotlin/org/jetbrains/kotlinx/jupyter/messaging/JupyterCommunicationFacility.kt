package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplInterruptedException
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.util.DefaultPromptOptions
import org.jetbrains.kotlinx.jupyter.util.Provider

interface JupyterCommunicationFacility {
    val socketManager: JupyterServerSockets
    val messageFactory: MessageFactory
}

class JupyterCommunicationFacilityImpl(
    override val socketManager: JupyterServerSockets,
    private val messageFactoryProvider: Provider<MessageFactory>,
) : JupyterCommunicationFacility {
    override val messageFactory: MessageFactory
        get() = messageFactoryProvider.provide() ?: throw ReplException("No context message provided")
}

fun JupyterCommunicationFacility.sendStatus(status: KernelStatus) {
    val message =
        messageFactory.makeReplyMessageOrNull(
            MessageType.STATUS,
            content = StatusMessage(status),
        ) ?: messageFactory.makeSimpleMessage(MessageType.STATUS, content = StatusMessage(status))
    socketManager.iopub.sendMessage(message)
}

fun JupyterCommunicationFacility.doWrappedInBusyIdle(action: () -> Unit) {
    sendStatus(KernelStatus.BUSY)
    tryFinally(
        action = action,
        finally = { sendStatus(KernelStatus.IDLE) },
    )
}

fun JupyterCommunicationFacility.sendSimpleMessageToIoPub(
    msgType: MessageType,
    content: MessageContent,
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
        messageFactory.makeReplyMessage(msgType = MessageType.STREAM, content = StreamMessage(stream.optionName(), text)),
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
    val exception = response.exception ?: ReplInterruptedException(response.stdErr)
    val replyContent: MessageReplyContent = exception.toExecuteErrorReply(executionCount)
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
