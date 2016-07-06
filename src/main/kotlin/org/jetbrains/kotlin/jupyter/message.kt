package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import java.text.SimpleDateFormat
import java.util.*

private val ISO8601DateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ")
internal val ISO8601DateNow: String get() = ISO8601DateFormatter.format(Date())

data class Message(
        val id: List<ByteArray>,
        val header: JsonObject,
        val parentHeader: JsonObject,
        val metadata: JsonObject,
        val content: JsonObject,
        val blob: ByteArray = byteArrayOf()
)

fun makeNewMessage(header: Map<String, Any?> = mapOf(),
                   parentHeader: Map<String, Any?> = mapOf(),
                   metadata: Map<String, Any?> = mapOf(),
                   content: Map<String, Any?> = mapOf()) =
        Message(id = listOf(UUID.randomUUID().toString().toByteArray()),
                header = JsonObject(header),
                parentHeader = JsonObject(parentHeader),
                metadata = JsonObject(metadata),
                content = JsonObject(content))

fun makeReplyMessage(msg: Message,
                     msgType: String? = null,
                     header: Map<String, Any?>? = null,
                     parentHeader: Map<String, Any?>? = null,
                     metadata: Map<String, Any?>? = null,
                     content: Map<String, Any?>? = null) =
        Message(id = msg.id,
                header = JsonObject(header ?: makeHeader(msgType!!, msg)),
                parentHeader = parentHeader?.let { JsonObject(it) } ?: msg.header,
                metadata = JsonObject(metadata ?: mapOf()),
                content = JsonObject(content ?: mapOf()))

fun makeHeader(msgType: String, incomingMsg: Message? = null, sessionId: String? = null): Map<String, Any?> = mapOf(
        "msg_id" to UUID.randomUUID().toString(),
        "date" to ISO8601DateNow,
        "version" to protocolVersion,
        "username" to ((incomingMsg?.header?.get("username") as? String) ?: "kernel"),
        "session" to ((incomingMsg?.header?.get("session") as? String) ?: sessionId),
        "msg_type" to msgType)