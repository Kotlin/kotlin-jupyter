package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.jetbrains.kotlinx.jupyter.util.jsonObject

const val EXECUTION_INTERRUPTED_MESSAGE = "The execution was interrupted"

abstract class Response(
    val stdOut: String?,
    val stdErr: String?,
) {
    abstract val state: ResponseState

    abstract fun sendBody(socketManager: JupyterBaseSockets, requestCount: Long, messageFactory: MessageFactory, startedTime: String)
}

class OkResponseWithMessage(
    private val result: DisplayResult?,
    private val metadata: EvaluatedSnippetMetadata? = null,
) : Response(null, null) {
    override val state: ResponseState = ResponseState.Ok

    override fun sendBody(
        socketManager: JupyterBaseSockets,
        requestCount: Long,
        messageFactory: MessageFactory,
        startedTime: String,
    ) {
        if (result != null) {
            val resultJson = result.toJson(Json.EMPTY, null)

            socketManager.iopub.sendMessage(
                messageFactory.makeReplyMessage(
                    MessageType.EXECUTE_RESULT,
                    content = ExecutionResultMessage(
                        executionCount = requestCount,
                        data = resultJson["data"]!!,
                        metadata = resultJson["metadata"]!!,
                    ),
                ),
            )
        }

        socketManager.shell.sendMessage(
            messageFactory.makeReplyMessage(
                MessageType.EXECUTE_REPLY,
                metadata = jsonObject(
                    "dependencies_met" to Json.encodeToJsonElement(true),
                    "engine" to (JsonPrimitive(messageFactory.sessionId)),
                    "status" to Json.encodeToJsonElement("ok"),
                    "started" to Json.encodeToJsonElement(startedTime),
                    "eval_metadata" to Json.encodeToJsonElement(metadata),
                ),
                content = ExecuteReply(
                    MessageStatus.OK,
                    requestCount,
                ),
            ),
        )
    }
}

class AbortResponseWithMessage(
    stdErr: String? = null,
) : Response(null, stdErr) {
    override val state: ResponseState = ResponseState.Abort

    override fun sendBody(
        socketManager: JupyterBaseSockets,
        requestCount: Long,
        messageFactory: MessageFactory,
        startedTime: String,
    ) {
        val errorReply = messageFactory.makeReplyMessage(
            MessageType.EXECUTE_REPLY,
            content = ExecuteReply(MessageStatus.ABORT, requestCount),
        )
        System.err.println("Sending abort: $errorReply")
        socketManager.shell.sendMessage(errorReply)
    }
}

class ErrorResponseWithMessage(
    stdErr: String? = null,
    private val errorName: String = "Unknown error",
    private var errorValue: String = "",
    private val traceback: List<String> = emptyList(),
    private val additionalInfo: JsonObject = Json.EMPTY,
) : Response(null, stdErr) {
    override val state: ResponseState = ResponseState.Error

    override fun sendBody(
        socketManager: JupyterBaseSockets,
        requestCount: Long,
        messageFactory: MessageFactory,
        startedTime: String,
    ) {
        val errorReply = messageFactory.makeReplyMessage(
            MessageType.EXECUTE_REPLY,
            content = ExecuteErrorReply(requestCount, errorName, errorValue, traceback, additionalInfo),
        )
        System.err.println("Sending error: $errorReply")
        socketManager.shell.sendMessage(errorReply)
    }
}
