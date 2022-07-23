package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.protocolVersion
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

private val ISO8601DateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ")
internal val ISO8601DateNow: String get() = ISO8601DateFormatter.format(Date())

internal val emptyJsonObjectString = Json.EMPTY.toString()
internal val emptyJsonObjectStringBytes = emptyJsonObjectString.toByteArray()

data class RawMessageImpl(
    override val id: List<ByteArray> = listOf(),
    override val header: JsonObject,
    override val parentHeader: JsonObject?,
    override val metadata: JsonObject?,
    override val content: JsonElement,
) : RawMessage {
    override fun toString(): String =
        "msg[${id.joinToString { it.toString(charset = Charsets.UTF_8) }}] $data"
}

fun RawMessage.toMessage(): Message {
    return Message(id, Json.decodeFromJsonElement(data))
}

fun messageDataJson(
    header: JsonObject,
    parentHeader: JsonObject?,
    metadata: JsonObject?,
    content: JsonElement,
) = buildJsonObject {
    put("header", header)
    put("parent_header", parentHeader ?: JsonNull)
    put("metadata", metadata ?: JsonNull)
    put("content", content)
}

val RawMessage.data: JsonObject get() = messageDataJson(header, parentHeader, metadata, content)

data class Message(
    val id: List<ByteArray> = listOf(),
    val data: MessageData = MessageData()
) {
    val type: MessageType
        get() = data.header!!.type

    val content: MessageContent
        get() = data.content!!

    override fun toString(): String =
        "msg[${id.joinToString { it.toString(charset = Charsets.UTF_8) }}] " +
            Json.encodeToString(data)
}

fun Message.toRawMessage(): RawMessage {
    val dataJson = Json.encodeToJsonElement(data).jsonObject
    return RawMessageImpl(
        id,
        dataJson["header"]!!.jsonObject,
        dataJson["parent_header"] as? JsonObject,
        dataJson["metadata"] as? JsonObject,
        dataJson["content"]!!,
    )
}

@JvmName("jsonObjectForString")
fun jsonObject(vararg namedValues: Pair<String, String?>): JsonObject = Json.encodeToJsonElement(hashMapOf(*namedValues)) as JsonObject

@JvmName("jsonObjectForInt")
fun jsonObject(vararg namedValues: Pair<String, Int>): JsonObject = Json.encodeToJsonElement(hashMapOf(*namedValues)) as JsonObject

@JvmName("jsonObjectForPrimitive")
fun jsonObject(vararg namedValues: Pair<String, JsonElement>): JsonObject = Json.encodeToJsonElement(hashMapOf(*namedValues)) as JsonObject

fun jsonObject(namedValues: Iterable<Pair<String, Any?>>): JsonObject = buildJsonObject {
    namedValues.forEach { (key, value) -> put(key, Json.encodeToJsonElement(value)) }
}

internal operator fun JsonObject?.get(key: String) = this?.get(key)

fun makeReplyMessage(
    msg: Message,
    msgType: MessageType? = null,
    sessionId: String? = null,
    header: MessageHeader? = null,
    parentHeader: MessageHeader? = null,
    metadata: JsonElement? = null,
    content: MessageContent? = null
) =
    Message(
        id = msg.id,
        MessageData(
            header = header ?: makeHeader(msgType = msgType, incomingMsg = msg, sessionId = sessionId),
            parentHeader = parentHeader ?: msg.data.header,
            metadata = metadata,
            content = content
        )
    )

fun makeHeader(msgType: MessageType? = null, incomingMsg: Message? = null, sessionId: String? = null): MessageHeader {
    val parentHeader = incomingMsg?.data?.header
    return makeHeader(
        msgType ?: MessageType.NONE,
        parentHeader?.session ?: sessionId,
        parentHeader?.username ?: "kernel",
    )
}

fun makeJsonHeader(msgType: String, incomingMsg: RawMessage? = null, sessionId: String? = null): JsonObject {
    val parentHeader = incomingMsg?.header
    fun JsonObject.getStringVal(key: String): String? {
        return get(key)?.jsonPrimitive?.content
    }

    val header = makeHeader(
        MessageType.fromString(msgType) ?: MessageType.NONE,
        parentHeader?.getStringVal("session") ?: sessionId,
        parentHeader?.getStringVal("username") ?: "kernel",
    )
    return Json.encodeToJsonElement(header).jsonObject
}

fun makeHeader(type: MessageType, sessionId: String?, username: String?): MessageHeader {
    return MessageHeader(
        UUID.randomUUID().toString(),
        type,
        sessionId,
        username,
        protocolVersion,
        ISO8601DateNow
    )
}
