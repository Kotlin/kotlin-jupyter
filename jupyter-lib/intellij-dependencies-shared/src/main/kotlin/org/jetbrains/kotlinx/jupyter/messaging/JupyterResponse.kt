package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplInterruptedException
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata

const val EXECUTION_INTERRUPTED_MESSAGE = "The execution was interrupted"

sealed interface JupyterResponse {
    val status: MessageStatus

    /**
     * Error message if any.
     * The value of this will depend on the implementation:
     * - [OkJupyterResponse]: Will be `null`
     * - [AbortJupyterResponse]: Will be the reason the execution was aborted.
     * - [ErrorJupyterResponse]: Will be the [exception] stacktrace.
     */
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
    override val exception: ReplException? = null,
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
    executionCount: ExecutionCount,
    startedTime: String,
) {
    when (response) {
        is AbortJupyterResponse -> {
            val errorMessage = response.stdErr!!
            sendOut(JupyterOutType.STDERR, errorMessage)
        }
        is ErrorJupyterResponse -> {
            sendError(response, executionCount, startedTime)
        }
        is OkJupyterResponse -> {
            sendExecuteResult(response.displayResult, executionCount)
        }
    }
    sendExecuteReply(response, executionCount, startedTime)
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
    executionCount: ExecutionCount,
) {
    if (result == null) return
    val resultJson = result.toJson(Json.EMPTY, null)

    socketManager.iopub.sendMessage(
        messageFactory.makeReplyMessage(
            MessageType.EXECUTE_RESULT,
            content =
                ExecuteResult(
                    executionCount = executionCount,
                    data = resultJson["data"]!!,
                    metadata = resultJson["metadata"]!!,
                ),
        ),
    )
}

fun JupyterCommunicationFacility.sendExecuteReply(
    response: JupyterResponse,
    executionCount: ExecutionCount,
    startedTime: String,
) {
    val replyMetadata =
        ExecuteReplyMetadata(
            true,
            messageFactory.sessionId,
            response.status,
            startedTime,
            response.metadata,
        )

    val replyContent =
        response.exception?.toExecuteErrorReply(executionCount)
            ?: when (response.status) {
                MessageStatus.ABORT -> {
                    ExecuteAbortReply()
                }
                MessageStatus.ERROR -> {
                    ReplInterruptedException(response.stdErr)
                        .toExecuteErrorReply(executionCount)
                }
                MessageStatus.OK -> {
                    ExecuteSuccessReply(executionCount)
                }
            }

    val reply =
        messageFactory.makeReplyMessage(
            MessageType.EXECUTE_REPLY,
            content = replyContent,
            metadata = MessageFormat.encodeToJsonElement(replyMetadata),
        )

    when (response.status) {
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

fun ReplException.toExecuteErrorReply(executionCount: ExecutionCount): ExecuteErrorReply =
    ExecuteErrorReply(
        executionCount,
        jupyterException.javaClass.canonicalName,
        jupyterException.message ?: "",
        traceback,
        getAdditionalInfoJson() ?: Json.EMPTY,
    )
