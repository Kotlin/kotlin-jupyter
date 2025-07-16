package org.jetbrains.kotlinx.jupyter.protocol.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Raw Jupyter message.
 */
interface RawMessage {
    val id: List<ByteArray>
    val header: JsonObject
    val parentHeader: JsonObject?
    val metadata: JsonObject?
    val content: JsonElement
}

val RawMessage.type: String?
    get() {
        val type = header["msg_type"]
        if (type !is JsonPrimitive || !type.isString) return null
        return type.content
    }

val RawMessage.sessionId: String? get() = header["session"]?.jsonPrimitive?.content
val RawMessage.username: String? get() = header["username"]?.jsonPrimitive?.content
typealias RawMessageAction = (RawMessage) -> Unit
