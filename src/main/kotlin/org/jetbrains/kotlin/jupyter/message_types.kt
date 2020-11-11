@file:UseSerializers(ScriptDiagnosticSerializer::class)

package org.jetbrains.kotlin.jupyter

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.script.experimental.api.ScriptDiagnostic

@Serializable(MessageTypeSerializer::class)
enum class MessageType(val contentClass: KClass<out MessageContent>) {
    NONE(AbortReply::class),

    EXECUTE_REQUEST(ExecuteRequest::class),
    EXECUTE_REPLY(ExecuteReply::class),
    EXECUTE_INPUT(ExecutionInputReply::class),
    EXECUTE_RESULT(ExecutionResult::class),

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

    LIST_ERRORS_REQUEST(ListErrorsRequest::class),
    LIST_ERRORS_REPLY(ListErrorsReply::class);

    val type: String
        get() = name.toLowerCase()
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

@Serializable
enum class MessageStatus {
    @SerialName("ok")
    OK,

    @SerialName("error")
    ERROR,

    @SerialName("abort")
    ABORT;
}

@Serializable(DetailsLevelSerializer::class)
enum class DetailLevel(val level: Int) {
    STANDARD(0),
    DETAILED(1);
}

@Serializable
enum class KernelStatus {
    @SerialName("busy")
    BUSY,

    @SerialName("idle")
    IDLE,

    @SerialName("starting")
    STARTING;
}

object MessageTypeSerializer : KSerializer<MessageType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            MessageType::class.qualifiedName!!,
            PrimitiveKind.STRING
        )

    override fun deserialize(decoder: Decoder): MessageType {
        val type = decoder.decodeString()
        return MessageType.values().first { it.type == type }
    }

    override fun serialize(encoder: Encoder, value: MessageType) {
        encoder.encodeString(value.type)
    }
}

object DetailsLevelSerializer : KSerializer<DetailLevel> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            DetailLevel::class.qualifiedName!!,
            PrimitiveKind.INT
        )

    override fun deserialize(decoder: Decoder): DetailLevel {
        val level = decoder.decodeInt()
        return DetailLevel.values().first { it.level == level }
    }

    override fun serialize(encoder: Encoder, value: DetailLevel) {
        encoder.encodeInt(value.level)
    }
}

@Serializable
abstract class MessageContent

@Serializable
abstract class MessageReplyContent(
    val status: MessageStatus
) : MessageContent()

@Serializable
class AbortReply : MessageReplyContent(MessageStatus.ABORT)

@Serializable
open class ErrorReply(
    @SerialName("ename")
    val name: String,

    @SerialName("evalue")
    val value: String,

    val traceback: List<String>,
) : MessageReplyContent(MessageStatus.ERROR)

@Serializable
class ExecuteErrorReply(
    @SerialName("execution_count")
    val executionCount: Long,

    @SerialName("ename")
    val name: String,

    @SerialName("evalue")
    val value: String,

    val traceback: List<String>,

    val additionalInfo: JsonObject,
) : MessageReplyContent(MessageStatus.ERROR)

@Serializable
abstract class OkReply : MessageReplyContent(MessageStatus.OK)

@Serializable
data class ExecuteRequest(
    val code: String,
    val silent: Boolean = false,

    @SerialName("store_history")
    val storeHistory: Boolean = true,

    @SerialName("user_expressions")
    val userExpressions: Map<String, String> = mapOf(),

    @SerialName("allow_stdin")
    val allowStdin: Boolean = true,

    @SerialName("stop_on_error")
    val stopOnError: Boolean = true,
) : MessageContent()

@Serializable
class Payload(
    val source: String
)

@Serializable
class ExecuteReply(
    val status: MessageStatus,

    @SerialName("execution_count")
    val executionCount: Long,

    val payload: List<Payload> = listOf(),

    @SerialName("user_expressions")
    val userExpressions: Map<String, JsonElement> = mapOf(),
) : MessageContent()

@Serializable
class InspectRequest(
    val code: String,

    @SerialName("cursor_pos")
    val cursorPos: Int,

    @SerialName("detail_level")
    val detailLevel: DetailLevel,
) : MessageContent()

@Serializable
class InspectReply(
    val found: Boolean,
    val data: JsonObject = emptyJsonObject,
    val metadata: JsonObject = emptyJsonObject,
) : OkReply()

@Serializable
class CompleteRequest(
    val code: String,

    @SerialName("cursor_pos")
    val cursorPos: Int,
) : MessageContent()

@Serializable
class Paragraph(
    val cursor: Int,
    val text: String,
)

@Serializable
class CompleteReply(
    val matches: List<String>,

    @SerialName("cursor_start")
    val cursorStart: Int,

    @SerialName("cursor_end")
    val cursorEnd: Int,

    val paragraph: Paragraph,

    val metadata: JsonObject = emptyJsonObject,
) : OkReply()

@Serializable
class IsCompleteRequest(
    val code: String,
) : MessageContent()

@Serializable
class IsCompleteReply(
    val status: String,
    val indent: String? = null,
) : MessageContent()

@Serializable
class KernelInfoRequest : MessageContent()

@Serializable
class HelpLink(
    val text: String,
    val url: String,
)

@Serializable
class LanguageInfo(
    val name: String,
    val version: String,
    val mimetype: String,

    @SerialName("file_extension")
    val fileExtension: String,

    @SerialName("pygments_lexer")
    val pygmentsLexer: String,

    @SerialName("codemirror_mode")
    val codemirrorMode: String,

    @SerialName("nbconvert_exporter")
    val nbConvertExporter: String,
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
class ShutdownRequest(
    val restart: Boolean,
) : MessageContent()

@Serializable
class ShutdownResponse(
    val restart: Boolean,
) : MessageContent()

@Serializable
class InterruptRequest : MessageContent()

@Serializable
class InterruptResponse : MessageContent()

@Serializable
class DebugRequest : MessageContent()

@Serializable
class DebugResponse : MessageContent()

@Serializable
class StreamResponse(
    val name: String,
    val text: String,
) : MessageContent()

@Serializable
class DisplayDataResponse(
    val data: JsonElement? = null,
    val metadata: JsonElement? = null,
    val transient: JsonElement? = null,
) : MessageContent()

@Serializable
class ExecutionInputReply(
    val code: String,

    @SerialName("execution_count")
    val executionCount: Long
) : MessageContent()

@Serializable
class ExecutionResult(
    val data: JsonElement,
    val metadata: JsonElement,

    @SerialName("execution_count")
    val executionCount: Long
) : MessageContent()

@Serializable
class StatusReply(
    @SerialName("execution_state")
    val status: KernelStatus,
) : MessageContent()

@Serializable
class ClearOutputReply(
    val wait: Boolean
) : MessageContent()

@Serializable
class DebugEventReply : MessageContent()

@Serializable
class InputRequest(
    val prompt: String,
    val password: Boolean = false,
) : MessageContent()

@Serializable
class InputReply(
    val value: String
) : MessageContent()

@Serializable
class HistoryRequest : MessageContent()

@Serializable
class HistoryReply(
    val history: List<String>
) : MessageContent()

@Serializable
class ConnectRequest : MessageContent()

@Serializable(ConnectReplySerializer::class)
class ConnectReply(
    val ports: JsonObject
) : MessageContent()

@Serializable
class CommInfoRequest(
    @SerialName("target_name")
    val targetName: String
) : MessageContent()

@Serializable
class Comm(
    @SerialName("target_name")
    val targetName: String
)

@Serializable
class CommInfoReply(
    val comms: Map<String, Comm>
) : MessageContent()

@Serializable
class ListErrorsRequest(
    val code: String,
) : MessageContent()

@Serializable
class ListErrorsReply(
    val code: String,

    val errors: List<ScriptDiagnostic>
) : MessageContent()

@Serializable(MessageDataSerializer::class)
data class MessageData(
    val header: MessageHeader? = null,
    val parentHeader: MessageHeader? = null,
    val metadata: JsonElement? = null,
    val content: MessageContent? = null,
)

object ScriptDiagnosticSerializer : KSerializer<ScriptDiagnostic> {
    override val descriptor: SerialDescriptor
        get() = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): ScriptDiagnostic {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: ScriptDiagnostic) {
        require(encoder is JsonEncoder)

        encoder.encodeJsonElement(
            buildJsonObject {
                put("message", JsonPrimitive(value.message))
                put("severity", JsonPrimitive(value.severity.name))

                val loc = value.location
                if (loc != null) {
                    val start = loc.start
                    val end = loc.end
                    put("start", jsonObject("line" to start.line, "col" to start.col))
                    if (end != null)
                        put("end", jsonObject("line" to end.line, "col" to end.col))
                }
            }
        )
    }
}

object MessageDataSerializer : KSerializer<MessageData> {
    @InternalSerializationApi
    private val contentSerializers = MessageType.values()
        .map { it.type to it.contentClass.serializer() }
        .toMap()

    override val descriptor: SerialDescriptor = serializer<JsonObject>().descriptor

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): MessageData {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val format = decoder.json

        val header = element["header"]?.let { format.decodeFromJsonElement<MessageHeader?>(it) }
        val parentHeader = element["parent_header"]?.let { format.decodeFromJsonElement<MessageHeader?>(it) }
        val metadata = element["metadata"]?.let { format.decodeFromJsonElement<JsonElement?>(it) }

        val content = if (header != null) {
            val contentSerializer = chooseSerializer(header.type)
            element["content"]?.let {
                format.decodeFromJsonElement(contentSerializer, it)
            }
        } else null

        return MessageData(header, parentHeader, metadata, content)
    }

    override fun serialize(encoder: Encoder, value: MessageData) {
        require(encoder is JsonEncoder)
        val format = encoder.json

        val content = value.content?.let {
            format.encodeToJsonElement(serializer(it::class.createType()), it)
        } ?: emptyJsonObject

        encoder.encodeJsonElement(
            JsonObject(
                mapOf(
                    "header" to format.encodeToJsonElement(value.header),
                    "parent_header" to format.encodeToJsonElement(value.parentHeader),
                    "metadata" to format.encodeToJsonElement(value.metadata),
                    "content" to content
                )
            )
        )
    }

    @InternalSerializationApi
    private fun chooseSerializer(messageType: MessageType): KSerializer<out MessageContent> {
        return contentSerializers[messageType.type] ?: throw ReplException("Unknown message type: $messageType")
    }
}

object ConnectReplySerializer : KSerializer<ConnectReply> {
    private val jsonSerializer = JsonObject.serializer()

    override val descriptor: SerialDescriptor = jsonSerializer.descriptor

    override fun deserialize(decoder: Decoder): ConnectReply {
        val json = decoder.decodeSerializableValue(jsonSerializer)
        return ConnectReply(json)
    }

    override fun serialize(encoder: Encoder, value: ConnectReply) {
        encoder.encodeSerializableValue(jsonSerializer, value.ports)
    }
}
