@file:Suppress("UNUSED")
@file:UseSerializers(ScriptDiagnosticSerializer::class)

package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
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
import org.jetbrains.kotlinx.jupyter.config.LanguageInfo
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.protocol.messageDataJson
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.jetbrains.kotlinx.jupyter.util.toUpperCaseAsciiOnly
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.script.experimental.api.ScriptDiagnostic

@Serializable(MessageTypeSerializer::class)
enum class MessageType(val contentClass: KClass<out MessageContent>) {
    NONE(ExecuteAbortReply::class),

    EXECUTE_REQUEST(ExecuteRequest::class),
    EXECUTE_REPLY(ExecuteReply::class),
    EXECUTE_INPUT(ExecutionInputReply::class),
    EXECUTE_RESULT(ExecutionResultMessage::class),

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

object MessageTypeSerializer : KSerializer<MessageType> {
    private val cache: MutableMap<String, MessageType> = ConcurrentHashMap()

    private fun getMessageType(type: String): MessageType {
        return cache.computeIfAbsent(type) { newType ->
            MessageType.entries.firstOrNull { it.type == newType }
                ?: throw SerializationException("Unknown message type: $newType")
        }
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            MessageType::class.qualifiedName!!,
            PrimitiveKind.STRING,
        )

    override fun deserialize(decoder: Decoder): MessageType {
        return getMessageType(decoder.decodeString())
    }

    override fun serialize(
        encoder: Encoder,
        value: MessageType,
    ) {
        encoder.encodeString(value.type)
    }
}

object DetailsLevelSerializer : KSerializer<DetailLevel> {
    private val cache: MutableMap<Int, DetailLevel> = ConcurrentHashMap()

    private fun getDetailsLevel(type: Int): DetailLevel {
        return cache.computeIfAbsent(type) { newLevel ->
            DetailLevel.entries.firstOrNull { it.level == newLevel }
                ?: throw SerializationException("Unknown details level: $newLevel")
        }
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            DetailLevel::class.qualifiedName!!,
            PrimitiveKind.INT,
        )

    override fun deserialize(decoder: Decoder): DetailLevel {
        return getDetailsLevel(decoder.decodeInt())
    }

    override fun serialize(
        encoder: Encoder,
        value: DetailLevel,
    ) {
        encoder.encodeInt(value.level)
    }
}

object ExecuteReplySerializer : KSerializer<ExecuteReply> {
    private val utilSerializer = serializer<JsonObject>()

    override val descriptor: SerialDescriptor
        get() = utilSerializer.descriptor

    override fun deserialize(decoder: Decoder): ExecuteReply {
        require(decoder is JsonDecoder)
        val json = decoder.decodeJsonElement().jsonObject
        val format = decoder.json

        val statusJson = json["status"] ?: throw SerializationException("Status field not present")

        val status = format.decodeFromJsonElement<MessageStatus>(statusJson)

        return when (status) {
            MessageStatus.OK -> format.decodeFromJsonElement<ExecuteSuccessReply>(json)
            MessageStatus.ERROR -> format.decodeFromJsonElement<ExecuteErrorReply>(json)
            MessageStatus.ABORT -> format.decodeFromJsonElement<ExecuteAbortReply>(json)
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: ExecuteReply,
    ) {
        when (value) {
            is ExecuteAbortReply -> encoder.encodeSerializableValue(serializer(), value)
            is ExecuteErrorReply -> encoder.encodeSerializableValue(serializer(), value)
            is ExecuteSuccessReply -> encoder.encodeSerializableValue(serializer(), value)
        }
    }
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
    val executionCount: Long,
    @SerialName("ename")
    val name: String,
    @SerialName("evalue")
    val value: String,
    val traceback: List<String>,
    val additionalInfo: JsonObject,
) : MessageReplyContent(MessageStatus.ERROR), ExecuteReply

@Serializable
class ExecuteSuccessReply(
    @SerialName("execution_count")
    val executionCount: Long,
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
    val executionCount: Long,
) : AbstractMessageContent()

@Serializable
class ExecutionResultMessage(
    val data: JsonElement,
    val metadata: JsonElement,
    @SerialName("execution_count")
    val executionCount: Long,
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

object ScriptDiagnosticSerializer : KSerializer<ScriptDiagnostic> {
    override val descriptor: SerialDescriptor
        get() = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): ScriptDiagnostic {
        TODO("Not yet implemented")
    }

    override fun serialize(
        encoder: Encoder,
        value: ScriptDiagnostic,
    ) {
        require(encoder is JsonEncoder)

        encoder.encodeJsonElement(
            buildJsonObject {
                put("message", JsonPrimitive(value.message))
                put("severity", JsonPrimitive(value.severity.name))

                val loc = value.location
                if (loc != null) {
                    val start = loc.start
                    val end = loc.end
                    put(
                        "start",
                        buildJsonObject {
                            put("line", JsonPrimitive(start.line))
                            put("col", JsonPrimitive(start.col))
                        },
                    )
                    if (end != null) {
                        put(
                            "end",
                            buildJsonObject {
                                put("line", JsonPrimitive(end.line))
                                put("col", JsonPrimitive(end.col))
                            },
                        )
                    }
                }
            },
        )
    }
}

object MessageDataSerializer : KSerializer<MessageData> {
    @InternalSerializationApi
    private val contentSerializers = MessageType.entries.associate { it.type to it.contentClass.serializer() }

    override val descriptor: SerialDescriptor = serializer<JsonObject>().descriptor

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(decoder: Decoder): MessageData {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val format = decoder.json

        val header = element["header"]?.let { format.decodeFromJsonElement<MessageHeader?>(it) }
        val parentHeader = element["parent_header"]?.let { format.decodeFromJsonElement<MessageHeader?>(it) }
        val metadata = element["metadata"]?.let { format.decodeFromJsonElement<JsonElement?>(it) }

        val content =
            if (header != null) {
                val contentSerializer = chooseSerializer(header.type)
                element["content"]?.let {
                    format.decodeFromJsonElement(contentSerializer, it)
                }
            } else {
                null
            }

        return MessageData(header, parentHeader, metadata, content)
    }

    override fun serialize(
        encoder: Encoder,
        value: MessageData,
    ) {
        require(encoder is JsonEncoder)
        val format = encoder.json

        val content =
            value.content?.let {
                format.encodeToJsonElement(serializer(it::class.createType()), it)
            } ?: Json.EMPTY

        encoder.encodeJsonElement(
            messageDataJson(
                format.encodeToJsonElement(value.header).jsonObject,
                value.parentHeader?.let { format.encodeToJsonElement(it) }?.jsonObject,
                value.metadata?.let { format.encodeToJsonElement(it) }?.jsonObject,
                content,
            ),
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

    override fun serialize(
        encoder: Encoder,
        value: ConnectReply,
    ) {
        encoder.encodeSerializableValue(jsonSerializer, value.ports)
    }
}
