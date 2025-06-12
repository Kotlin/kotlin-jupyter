package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.messageAndStackTrace
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.util.EMPTY

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
                MessageStatus.ABORT -> ExecuteAbortReply()
                MessageStatus.ERROR -> toAbortErrorReply(executionCount, response.stdErr)
                MessageStatus.OK -> ExecuteSuccessReply(executionCount)
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
        javaClass.canonicalName,
        message ?: "",
        messageAndStackTrace(false).lines(),
        getAdditionalInfoJson() ?: Json.EMPTY,
    )

/**
 * For errors in the user's REPL code, return a reply that includes the
 * location in the user's code if it is available.
 */
fun ReplEvalRuntimeException.toExecuteErrorReply(executionCount: ExecutionCount): ExecuteErrorReply {
    val userException = this.cause!!
    val traceBack =
        buildString {
            // IntelliJ will check for the first occurrence of "\n<exceptionType>: <exceptionMessage>"`
            // (note the newline) and hide everything above it under a fold named "Stacktrace..."
            //
            // So by printing the full stacktrace as the first thing, we ensure that it gets
            // hidden by default and only the top exception type/message + cell information
            // is shown to the user.
            userException
                .stackTraceToString()
                .lines()
                .mapIndexed { index, line ->
                    // Account for the first line being the exception type + message and last (empty) line
                    // being created by stackTraceToString()
                    val errorMetadata = if (index >= 0 && index < cellErrorLocations.size) cellErrorLocations[index] else null
                    errorMetadata?.let { metadata ->
                        line +
                            when (metadata.lineNumber <= metadata.visibleSourceLines) {
                                true -> " at Cell In[${metadata.jupyterRequestCount}], line ${metadata.lineNumber}"
                                false -> " at Cell In[${metadata.jupyterRequestCount}]"
                            }
                    } ?: line
                }.forEach {
                    if (it.isNotEmpty()) appendLine(it)
                }
            appendLine()
            appendLine("${userException.javaClass.canonicalName}: ${userException.message}")
            val topError = cellErrorLocations.firstOrNull { it != null }
            if (topError != null) {
                if (topError.lineNumber > topError.visibleSourceLines) {
                    appendLine("at Cell In[${topError.jupyterRequestCount}]")
                } else {
                    appendLine("at Cell In[${topError.jupyterRequestCount}], line ${topError.lineNumber}")
                }
            }
        }
    return ExecuteErrorReply(
        executionCount,
        userException.javaClass.canonicalName,
        userException.message ?: "",
        traceBack.lines(),
        getAdditionalInfoJson() ?: Json.EMPTY,
    )
}
