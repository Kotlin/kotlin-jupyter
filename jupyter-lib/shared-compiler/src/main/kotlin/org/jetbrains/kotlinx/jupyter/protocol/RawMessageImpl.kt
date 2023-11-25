package org.jetbrains.kotlinx.jupyter.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

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

val MessageFormat = Json {
    ignoreUnknownKeys = true
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
