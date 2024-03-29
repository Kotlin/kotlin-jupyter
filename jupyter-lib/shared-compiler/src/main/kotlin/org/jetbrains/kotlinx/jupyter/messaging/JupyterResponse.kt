package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.util.EMPTY

const val EXECUTION_INTERRUPTED_MESSAGE = "The execution was interrupted"

interface JupyterResponse {
    val status: MessageStatus
    val stdErr: String? get() = null
    val displayResult: DisplayResult? get() = null
    val exception: ReplException? get() = null
    val metadata: EvaluatedSnippetMetadata? get() = null
}

class OkJupyterResponse(
    override val displayResult: DisplayResult?,
    override val metadata: EvaluatedSnippetMetadata? = null,
) : JupyterResponse {
    override val status: MessageStatus get() = MessageStatus.OK
}

class ErrorJupyterResponse(
    override val stdErr: String?,
    override val exception: ReplException?,
    override val metadata: EvaluatedSnippetMetadata? = null,
) : JupyterResponse {
    override val status: MessageStatus get() = MessageStatus.ERROR
}

class AbortJupyterResponse(
    override val stdErr: String?,
    override val metadata: EvaluatedSnippetMetadata? = null,
) : JupyterResponse {
    override val status: MessageStatus get() = MessageStatus.ABORT
}

fun JupyterCommunicationFacility.sendResponse(
    response: JupyterResponse,
    requestCount: Long,
    startedTime: String,
) {
    val stdErr = response.stdErr
    if (!stdErr.isNullOrEmpty()) {
        sendOut(JupyterOutType.STDERR, stdErr)
    }

    sendExecuteResult(response.displayResult, requestCount)
    sendExecuteReply(response.status, response.exception, requestCount, startedTime, response.metadata)
}

@Serializable
data class ExecuteReplyMetadata(
    @SerialName("dependencies_met")
    val dependenciesMet: Boolean = true,
    val engine: String,
    val status: MessageStatus,
    @SerialName("started")
    val startedTime: String,
    @SerialName("eval_metadata")
    val evalMetadata: EvaluatedSnippetMetadata?,
)

fun JupyterCommunicationFacility.sendExecuteResult(
    result: DisplayResult?,
    requestCount: Long,
) {
    if (result == null) return
    val resultJson = result.toJson(Json.EMPTY, null)

    socketManager.iopub.sendMessage(
        messageFactory.makeReplyMessage(
            MessageType.EXECUTE_RESULT,
            content =
                ExecutionResultMessage(
                    executionCount = requestCount,
                    data = resultJson["data"]!!,
                    metadata = resultJson["metadata"]!!,
                ),
        ),
    )
}

fun JupyterCommunicationFacility.sendExecuteReply(
    status: MessageStatus,
    exception: ReplException?,
    requestCount: Long,
    startedTime: String,
    metadata: EvaluatedSnippetMetadata? = null,
) {
    val replyMetadata =
        ExecuteReplyMetadata(
            true,
            messageFactory.sessionId,
            status,
            startedTime,
            metadata,
        )

    val replyContent =
        exception?.toExecuteErrorReply(requestCount)
            ?: when (status) {
                MessageStatus.ERROR, MessageStatus.ABORT -> ExecuteAbortReply()
                MessageStatus.OK -> ExecuteSuccessReply(requestCount)
            }

    val reply =
        messageFactory.makeReplyMessage(
            MessageType.EXECUTE_REPLY,
            content = replyContent as MessageReplyContent,
            metadata = MessageFormat.encodeToJsonElement(replyMetadata),
        )

    when (status) {
        MessageStatus.ERROR -> System.err.println("Sending error: $reply")
        MessageStatus.ABORT -> System.err.println("Sending abort: $reply")
        MessageStatus.OK -> {}
    }

    socketManager.shell.sendMessage(reply)
}

fun Throwable.toErrorJupyterResponse(metadata: EvaluatedSnippetMetadata? = null): JupyterResponse {
    val exception = this
    if (exception !is ReplException) throw exception
    return ErrorJupyterResponse(exception.render(), exception, metadata)
}

fun ReplException.toExecuteErrorReply(requestCount: Long): ExecuteErrorReply {
    return ExecuteErrorReply(
        requestCount,
        javaClass.canonicalName,
        message ?: "",
        stackTrace.map { it.toString() },
        getAdditionalInfoJson() ?: Json.EMPTY,
    )
}
