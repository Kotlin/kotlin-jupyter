package org.jetbrains.kotlin.jupyter

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

private val ISO8601DateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ")
internal val ISO8601DateNow: String get() = ISO8601DateFormatter.format(Date())

val emptyJsonObject = JsonObject(mapOf())
internal val emptyJsonObjectString = emptyJsonObject.toString()
internal val emptyJsonObjectStringBytes = emptyJsonObjectString.toByteArray()

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
    val header = incomingMsg?.data?.header
    return MessageHeader(
        UUID.randomUUID().toString(),
        msgType ?: MessageType.NONE,
        header?.session ?: sessionId,
        header?.username ?: "kernel",
        protocolVersion,
        ISO8601DateNow
    )
}
