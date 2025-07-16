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
import org.jetbrains.kotlinx.jupyter.messaging.serializers.UpdateClientMetadataReplySerializer
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.util.toUpperCaseAsciiOnly
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptDiagnostic

/**
 * See https://jupyter-client.readthedocs.io/en/latest/messaging.html#messages-on-the-shell-router-dealer-channel
 * for details on how these messages are defined and how they are used in the Jupyter protocol.
 *
 * As we control the server when running notebooks inside IntelliJ, some custom message types have been introduced
 * to enhance the user experience. These are marked specifically below.
 *
 * Note about naming: All message classes are named after their protocol description, i.e. `execute_request` is
 * `ExecuteRequest` and `interrupt_reply` is `InterruptReply`. However, not all messages have either the suffix
 * `<X>Reply` or `<X>Request`, like `stream`. In that case the suffix `<X>Message` is used to make the class name
 * more explicit, so `stream` becomes `StreamMessage`.
 */
@Serializable(MessageTypeSerializer::class)
enum class MessageType(
    val contentClass: KClass<out MessageContent>,
) {
    NONE(ExecuteAbortReply::class),

    EXECUTE_REQUEST(ExecuteRequest::class),
    EXECUTE_REPLY(ExecuteReply::class),
    EXECUTE_INPUT(ExecuteInput::class),
    EXECUTE_RESULT(ExecuteResult::class),

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
    SHUTDOWN_REPLY(ShutdownReply::class),

    INTERRUPT_REQUEST(InterruptRequest::class),
    INTERRUPT_REPLY(InterruptReply::class),

    DEBUG_REQUEST(DebugRequest::class),
    DEBUG_REPLY(DebugReply::class),

    STREAM(StreamMessage::class),

    DISPLAY_DATA(DisplayDataMessage::class),
    UPDATE_DISPLAY_DATA(DisplayDataMessage::class),

    STATUS(StatusMessage::class),

    CLEAR_OUTPUT(ClearOutputMessage::class),

    DEBUG_EVENT(DebugEventMessage::class),

    INPUT_REQUEST(InputRequest::class),
    INPUT_REPLY(InputReply::class),

    HISTORY_REQUEST(HistoryRequest::class),
    HISTORY_REPLY(HistoryReply::class),

    CONNECT_REQUEST(ConnectRequest::class),
    CONNECT_REPLY(ConnectReply::class),

    COMM_INFO_REQUEST(CommInfoRequest::class),
    COMM_INFO_REPLY(CommInfoReply::class),

    COMM_OPEN(CommOpenMessage::class),
    COMM_MSG(CommMsgMessage::class),
    COMM_CLOSE(CommCloseMessage::class),

    // Custom extension message for Jupyter Web
    // See https://github.com/Kotlin/kotlin-jupyter/blob/98782fe656e1569734b66e8af0f9ece7360a2a2a/resources/notebook-extension/kernel.js#L1021
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
        fun fromString(type: String): MessageType? =
            try {
                MessageType.valueOf(type.toUpperCaseAsciiOnly())
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#message-header
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

// Wrapper for `detail_level`
// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#introspection
@Serializable(DetailsLevelSerializer::class)
enum class DetailLevel(
    val level: Int,
) {
    STANDARD(0),
    DETAILED(1),
}

// Wrapper for the `execution_state` in the `status` message which reports
// the Kernel Status.
// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#kernel-status
@Serializable
enum class KernelStatus {
    @SerialName("busy")
    BUSY,

    @SerialName("idle")
    IDLE,

    @SerialName("starting")
    STARTING,
}

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#execute
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
) : MessageContent

// Marker interface making it easier to work with serialization.
// This should ideally be a `MessageReplyContent`, but due to
// conflicting priorities (need to serialize `status` field, only
// having a single representation of reply types) it has to be
// of the slightly wrong type.
@Serializable(with = ExecuteReplySerializer::class)
sealed interface ExecuteReply : MessageContent

@Serializable
class ExecuteAbortReply :
    AbortReplyContent(),
    ExecuteReply

@Serializable
class ExecuteErrorReply(
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
    @SerialName("ename")
    override val name: String,
    @SerialName("evalue")
    override val value: String,
    /**
     * The full error, line by line, that will be displayed to the user.
     * It should contain all information relevant to the exception,
     * including message, exception type, stack trace and cell information.
     */
    override val traceback: List<String>,
    val additionalInfo: JsonObject,
) : ErrorReplyContent(),
    ExecuteReply

@Serializable
class ExecuteSuccessReply(
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
    val payload: List<Payload> = listOf(),
    @SerialName("user_expressions")
    val userExpressions: Map<String, JsonElement> = mapOf(),
    val additionalInfo: JsonObject? = null,
) : OkReplyContent(),
    ExecuteReply

@Serializable
class Payload(
    val source: String,
)

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#introspection
@Serializable
class InspectRequest(
    val code: String,
    @SerialName("cursor_pos")
    val cursorPos: Int,
    @SerialName("detail_level")
    val detailLevel: DetailLevel,
) : MessageContent

@Serializable
class InspectReply(
    val found: Boolean,
    val data: JsonObject = Json.EMPTY,
    val metadata: JsonObject = Json.EMPTY,
) : OkReplyContent()

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#completion
@Serializable
class CompleteRequest(
    val code: String,
    @SerialName("cursor_pos")
    val cursorPos: Int,
) : MessageContent

@Serializable
class CompleteErrorReply(
    @SerialName("ename")
    override val name: String,
    @SerialName("evalue")
    override val value: String,
    override val traceback: List<String>,
) : ErrorReplyContent()

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#code-completeness
// The reply message for this type has a `status` field that is different from the
// standard `status` field in request-reply messages. So it is handled specifically here.
@Serializable
class IsCompleteRequest(
    val code: String,
) : MessageContent

@Serializable
class IsCompleteReply(
    val status: String,
    val indent: String? = null,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#kernel-info
@Serializable
class KernelInfoRequest : MessageContent

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
) : OkReplyContent()

@Serializable
class KernelInfoReplyMetadata(
    val state: EvaluatedSnippetMetadata,
)

@Serializable
class HelpLink(
    val text: String,
    val url: String,
)

// Custom message used by the Notebook Plugin in IntelliJ.
// Currently, we only support returning OK or ABORT
@Serializable
class UpdateClientMetadataRequest(
    @Serializable(with = PathSerializer::class)
    val absoluteNotebookFilePath: Path,
) : MessageContent {
    init {
        if (!absoluteNotebookFilePath.isAbsolute) {
            throw IllegalArgumentException("Path arguments must be absolute: $absoluteNotebookFilePath")
        }
    }
}

// Marker interface making it easier to work with serialization.
// This should ideally be a `MessageReplyContent`, but we due to
// conflicting priorities (need to serialize `status` field, only
// having a single representation of reply types) it has to be
// of the slightly wrong type.
@Serializable(with = UpdateClientMetadataReplySerializer::class)
sealed interface UpdateClientMetadataReply : MessageContent

@Serializable
class UpdateClientMetadataSuccessReply :
    OkReplyContent(),
    UpdateClientMetadataReply

@Serializable
class UpdateClientMetadataErrorReply(
    @SerialName("ename")
    override val name: String,
    @SerialName("evalue")
    override val value: String,
    override val traceback: List<String>,
) : ErrorReplyContent(),
    UpdateClientMetadataReply {
    constructor (ex: Exception) : this(
        ex.javaClass.simpleName,
        ex.message ?: "",
        ex.stackTrace.map { it.toString() },
    )
}

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#kernel-shutdown
@Serializable
class ShutdownRequest(
    val restart: Boolean,
) : MessageContent

@Serializable
class ShutdownReply(
    val restart: Boolean,
) : OkReplyContent()

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#kernel-interrupt
@Serializable
class InterruptRequest : MessageContent

@Serializable
class InterruptReply : OkReplyContent()

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#debug-request
@Serializable
class DebugRequest : MessageContent

@Serializable
class DebugReply : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#streams-stdout-stderr-etc
@Serializable
class StreamMessage(
    val name: String,
    val text: String,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#display-data
// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#update-display-data
@Serializable
class DisplayDataMessage(
    val data: JsonElement? = null,
    val metadata: JsonElement? = null,
    val transient: JsonElement? = null,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#code-inputs
@Serializable
class ExecuteInput(
    val code: String,
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#id7
@Serializable
class ExecuteResult(
    val data: JsonElement,
    val metadata: JsonElement,
    @SerialName("execution_count")
    val executionCount: ExecutionCount,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#kernel-status
@Serializable
class StatusMessage(
    @SerialName("execution_state")
    val status: KernelStatus,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#clear-output
@Serializable
class ClearOutputMessage(
    val wait: Boolean,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#debug-event
@Serializable
class DebugEventMessage : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#messages-on-the-stdin-router-dealer-channel
// Note, the reply message does not have a status field.
@Serializable
class InputRequest(
    val prompt: String,
    val password: Boolean = false,
) : MessageContent

@Serializable
class InputReply(
    val value: String,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#history
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
) : MessageContent

@Serializable
class HistoryReply(
    val history: List<String>,
) : OkReplyContent()

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#connect
@Serializable
class ConnectRequest : MessageContent

@Serializable(ConnectReplySerializer::class)
class ConnectReply(
    val ports: JsonObject,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#comm-info
@Serializable
class CommInfoRequest(
    @SerialName("target_name")
    val targetName: String? = null,
) : MessageContent

@Serializable
class CommInfoReply(
    val comms: Map<String, Comm>,
) : OkReplyContent()

// Wrapper for `comm` targets.
// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#comm-info
@Serializable
class Comm(
    @SerialName("target_name")
    val targetName: String,
)

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#opening-a-comm
@Serializable
class CommOpenMessage(
    @SerialName("comm_id")
    val commId: String,
    @SerialName("target_name")
    val targetName: String,
    val data: JsonObject = Json.EMPTY,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#comm-messages
@Serializable
class CommMsgMessage(
    @SerialName("comm_id")
    val commId: String,
    val data: JsonObject = Json.EMPTY,
) : MessageContent

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#tearing-down-comms
@Serializable
class CommCloseMessage(
    @SerialName("comm_id")
    val commId: String,
    val data: JsonObject = Json.EMPTY,
) : MessageContent

// Custom messages for expanded error reporting on the Jupyter Web Client
// See https://github.com/Kotlin/kotlin-jupyter/blob/98782fe656e1569734b66e8af0f9ece7360a2a2a/resources/notebook-extension/kernel.js#L1021
@Serializable
class ListErrorsRequest(
    val code: String,
) : MessageContent

@Serializable
class ListErrorsReply(
    val code: String,
    val errors: List<ScriptDiagnostic>,
) : MessageContent

/**
 * Class representing general messages sent across the Jupyter Protocol.
 * See https://jupyter-client.readthedocs.io/en/latest/messaging.html#a-full-message
 */
@Serializable(MessageDataSerializer::class)
data class MessageData(
    val header: MessageHeader? = null,
    val parentHeader: MessageHeader? = null,
    val metadata: JsonElement? = null,
    val content: MessageContent? = null,
)
