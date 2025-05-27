@file:Suppress("UNUSED")
@file:UseSerializers(ScriptDiagnosticSerializer::class)

package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.config.LanguageInfo
import org.jetbrains.kotlinx.jupyter.messaging.serializers.ConnectReplySerializer
import org.jetbrains.kotlinx.jupyter.messaging.serializers.DetailsLevelSerializer
import org.jetbrains.kotlinx.jupyter.messaging.serializers.ExecuteReplySerializer
import org.jetbrains.kotlinx.jupyter.messaging.serializers.MessageDataSerializer
import org.jetbrains.kotlinx.jupyter.messaging.serializers.MessageTypeSerializer
import org.jetbrains.kotlinx.jupyter.messaging.serializers.PathSerializer
import org.jetbrains.kotlinx.jupyter.messaging.serializers.ScriptDiagnosticSerializer
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.jetbrains.kotlinx.jupyter.util.toUpperCaseAsciiOnly
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptDiagnostic

/**
 * See https://jupyter-client.readthedocs.io/en/latest/messaging.html#messages-on-the-shell-router-dealer-channel
 * for details on how these messages are defined and how they are used in the Jupyter protocol.
 *
 * As we control the server when running notebooks inside IntelliJ, some custom message types have been introduced
 * to enhance the user experience. These are marked below
 */
@Serializable(MessageTypeSerializer::class)
enum class MessageType(val contentClass: KClass<out MessageContent>) {
    NONE(ExecuteAbortReply::class),

    EXECUTE_REQUEST(ExecuteRequest::class),
    EXECUTE_REPLY(ExecuteReply::class),
    EXECUTE_INPUT(ExecutionInputReply::class),
    EXECUTE_RESULT(ExecutionResultMessage::class),

    // "error" is not a message type defined by the protocol, but is a custom
    // type used by the Jupyter plugin to route errors to the appropriate error
    // console view.
    ERROR(ExecuteErrorReply::class),

    INSPECT_REQUEST(InspectRequest::class),
    INSPECT_REPLY(InspectReply::class),

    COMPLETE_REQUEST(CompleteRequest::class),
    COMPLETE_REPLY(CompleteReply::class),

    IS_COMPLETE_REQUEST(IsCompleteRequest::class),
    IS_COMPLETE_REPLY(IsCompleteReply::class),

    KERNEL_INFO_REQUEST(KernelInfoRequest::class),
    KERNEL_INFO_REPLY(KernelInfoReply::class),

    SHUTDOWN_REQUEST(ShutdownRequest::class),
    SHUTDOWN_REPLY(ShutdownResponse::class),

    INTERRUPT_REQUEST(InterruptRequest::class),
    INTERRUPT_REPLY(InterruptResponse::class),

    DEBUG_REQUEST(DebugRequest::class),
    DEBUG_REPLY(DebugResponse::class),

    STREAM(StreamResponse::class),

    DISPLAY_DATA(DisplayDataResponse::class),
    UPDATE_DISPLAY_DATA(DisplayDataResponse::class),

    STATUS(StatusReply::class),

    CLEAR_OUTPUT(ClearOutputReply::class),

    DEBUG_EVENT(DebugEventReply::class),

    INPUT_REQUEST(InputRequest::class),
    INPUT_REPLY(InputReply::class),

    HISTORY_REQUEST(HistoryRequest::class),
    HISTORY_REPLY(HistoryReply::class),

    CONNECT_REQUEST(ConnectRequest::class),
    CONNECT_REPLY(ConnectReply::class),

    COMM_INFO_REQUEST(CommInfoRequest::class),
    COMM_INFO_REPLY(CommInfoReply::class),

    COMM_OPEN(CommOpen::class),
    COMM_MSG(CommMsg::class),
    COMM_CLOSE(CommClose::class),

    LIST_ERRORS_REQUEST(ListErrorsRequest::class),
    LIST_ERRORS_REPLY(ListErrorsReply::class),

    // Custom message sent by the server (IntelliJ) to the kernel with
    // information about the location of the notebook file. This message should
    // be sent before compiling the first cell, and if the notebook file is
    // moved or renamed. Note, this only works correctly as long as there is only
    // one notebook file pr. kernel instance.
    UPDATE_CLIENT_METADATA_REQUEST(UpdateClientMetadataRequest::class),
    UPDATE_CLIENT_METADATA_REPLY(UpdateClientMetadataReply::class),
    ;

    val type: String
        get() = name.lowercase()

    companion object {
        fun fromString(type: String): MessageType? {
            return try {
                MessageType.valueOf(type.toUpperCaseAsciiOnly())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

@Serializable
data class MessageHeader(
    @SerialName("msg_id")
    val id: String,
    @SerialName("msg_type")
    val type: MessageType,
    val session: String? = null,
    val username: String? = null,
    val version: String? = null,
    val date: String? = null,
)

@Serializable
class MessageMetadata

@Serializable(DetailsLevelSerializer::class)
enum class DetailLevel(val level: Int) {
    STANDARD(0),
    DETAILED(1),
}

@Serializable
enum class KernelStatus {
    @SerialName("busy")
    BUSY,

    @SerialName("idle")
    IDLE,

    @SerialName("starting")
    STARTING,
}

@Serializable
open class CompleteErrorReply(
    @SerialName("ename")
    val name: String,
    @SerialName("evalue")
    val value: String,
    val traceback: List<String>,
) : MessageReplyContent(MessageStatus.ERROR)

@Serializable(ExecuteReplySerializer::class)
sealed interface ExecuteReply : MessageContent {
    val status: MessageStatus
}

@Serializable
@Suppress("CanSealedSubClassBeObject")
class ExecuteAbortReply : MessageReplyContent(MessageStatus.ABORT), ExecuteReply

@Serializable
class ExecuteErrorReply(
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
    @SerialName("ename")
    val name: String,
    @SerialName("evalue")
    val value: String,
    /**
     * The full error, line by line, that will be displayed to the user.
     * It should contain all information relevant to the exception,
     * including message, exception type, stack trace and cell information.
     */
    val traceback: List<String>,
    val additionalInfo: JsonObject,
) : MessageReplyContent(MessageStatus.ERROR), ExecuteReply

@Serializable
class ExecuteSuccessReply(
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
    val payload: List<Payload> = listOf(),
    @SerialName("user_expressions")
    val userExpressions: Map<String, JsonElement> = mapOf(),
    val additionalInfo: JsonObject? = null,
) : MessageReplyContent(MessageStatus.OK), ExecuteReply

@Serializable
data class ExecuteRequest(
    val code: String,
    val silent: Boolean = false,
    @SerialName("store_history")
    val storeHistory: Boolean = true,
    @SerialName("user_expressions")
    val userExpressions: Map<String, String> = mapOf(),
    @SerialName("user_variables")
    val userVariables: List<String> = listOf(),
    @SerialName("allow_stdin")
    val allowStdin: Boolean = true,
    @SerialName("stop_on_error")
    val stopOnError: Boolean = true,
) : AbstractMessageContent()

@Serializable
class Payload(
    val source: String,
)

@Serializable
class InspectRequest(
    val code: String,
    @SerialName("cursor_pos")
    val cursorPos: Int,
    @SerialName("detail_level")
    val detailLevel: DetailLevel,
) : AbstractMessageContent()

@Serializable
class InspectReply(
    val found: Boolean,
    val data: JsonObject = Json.EMPTY,
    val metadata: JsonObject = Json.EMPTY,
) : OkReply()

@Serializable
class CompleteRequest(
    val code: String,
    @SerialName("cursor_pos")
    val cursorPos: Int,
) : AbstractMessageContent()

@Serializable
class IsCompleteRequest(
    val code: String,
) : AbstractMessageContent()

@Serializable
class IsCompleteReply(
    val status: String,
    val indent: String? = null,
) : AbstractMessageContent()

@Serializable
class KernelInfoRequest : AbstractMessageContent()

@Serializable
class UpdateClientMetadataRequest(
    @Serializable(with = PathSerializer::class)
    val absoluteNotebookFilePath: Path,
) : AbstractMessageContent() {
    init {
        if (!absoluteNotebookFilePath.isAbsolute) {
            throw IllegalArgumentException("Path arguments must be absolute: $absoluteNotebookFilePath")
        }
    }
}

@Serializable
class UpdateClientMetadataReply(
    val status: MessageStatus,
) : AbstractMessageContent()

@Serializable
class HelpLink(
    val text: String,
    val url: String,
)

@Serializable
class KernelInfoReply(
    @SerialName("protocol_version")
    val protocolVersion: String,
    val implementation: String,
    @SerialName("implementation_version")
    val implementationVersion: String,
    val banner: String,
    @SerialName("language_info")
    val languageInfo: LanguageInfo,
    @SerialName("help_links")
    val helpLinks: List<HelpLink>,
) : OkReply()

@Serializable
class KernelInfoReplyMetadata(
    val state: EvaluatedSnippetMetadata,
)

@Serializable
class ShutdownRequest(
    val restart: Boolean,
) : AbstractMessageContent()

@Serializable
class ShutdownResponse(
    val restart: Boolean,
) : AbstractMessageContent()

@Serializable
class InterruptRequest : AbstractMessageContent()

@Serializable
class InterruptResponse : AbstractMessageContent()

@Serializable
class DebugRequest : AbstractMessageContent()

@Serializable
class DebugResponse : AbstractMessageContent()

@Serializable
class StreamResponse(
    val name: String,
    val text: String,
) : AbstractMessageContent()

@Serializable
class DisplayDataResponse(
    val data: JsonElement? = null,
    val metadata: JsonElement? = null,
    val transient: JsonElement? = null,
) : AbstractMessageContent()

@Serializable
class ExecutionInputReply(
    val code: String,
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
) : AbstractMessageContent()

@Serializable
class ExecutionResultMessage(
    val data: JsonElement,
    val metadata: JsonElement,
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
) : AbstractMessageContent()

@Serializable
class StatusReply(
    @SerialName("execution_state")
    val status: KernelStatus,
) : AbstractMessageContent()

@Serializable
class ClearOutputReply(
    val wait: Boolean,
) : AbstractMessageContent()

@Serializable
class DebugEventReply : AbstractMessageContent()

@Serializable
class InputRequest(
    val prompt: String,
    val password: Boolean = false,
) : AbstractMessageContent()

@Serializable
class InputReply(
    val value: String,
) : AbstractMessageContent()

@Serializable
class HistoryRequest(
    val output: Boolean,
    val raw: Boolean,
    @Suppress("PropertyName")
    val hist_access_type: String,
    // If hist_access_type is 'range'
    val session: Int? = null,
    val start: Int? = null,
    val stop: Int? = null,
    // hist_access_type is 'tail' or 'search'
    val n: Int? = null,
    // If hist_access_type is 'search'
    val pattern: String? = null,
    val unique: Boolean? = null,
) : AbstractMessageContent()

@Serializable
class HistoryReply(
    val history: List<String>,
) : AbstractMessageContent()

@Serializable
class ConnectRequest : AbstractMessageContent()

@Serializable(ConnectReplySerializer::class)
class ConnectReply(
    val ports: JsonObject,
) : AbstractMessageContent()

@Serializable
class CommInfoRequest(
    @SerialName("target_name")
    val targetName: String? = null,
) : AbstractMessageContent()

@Serializable
class Comm(
    @SerialName("target_name")
    val targetName: String,
)

@Serializable
class CommInfoReply(
    val comms: Map<String, Comm>,
) : AbstractMessageContent()

@Serializable
class CommOpen(
    @SerialName("comm_id")
    val commId: String,
    @SerialName("target_name")
    val targetName: String,
    val data: JsonObject = Json.EMPTY,
) : AbstractMessageContent()

@Serializable
class CommMsg(
    @SerialName("comm_id")
    val commId: String,
    val data: JsonObject = Json.EMPTY,
) : AbstractMessageContent()

@Serializable
class CommClose(
    @SerialName("comm_id")
    val commId: String,
    val data: JsonObject = Json.EMPTY,
) : AbstractMessageContent()

@Serializable
class ListErrorsRequest(
    val code: String,
) : AbstractMessageContent()

@Serializable
class ListErrorsReply(
    val code: String,
    val errors: List<ScriptDiagnostic>,
) : AbstractMessageContent()

@Serializable(MessageDataSerializer::class)
data class MessageData(
    val header: MessageHeader? = null,
    val parentHeader: MessageHeader? = null,
    val metadata: JsonElement? = null,
    val content: MessageContent? = null,
)
