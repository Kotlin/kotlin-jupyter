package org.jetbrains.kotlinx.jupyter.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import java.util.Base64

data class RawMessageImpl(
    override val id: List<ByteArray> = emptyList(),
    override val header: JsonObject,
    override val parentHeader: JsonObject?,
    override val metadata: JsonObject?,
    override val content: JsonElement,
    override val buffers: List<ByteArray> = emptyList(),
) : RawMessage {
    override fun toString(): String =
        buildMessageDebugString(
            id,
            MessageFormat.encodeToString(data),
            buffers,
        )
}

fun buildMessageDebugString(
    id: List<ByteArray>,
    data: String,
    buffers: List<ByteArray>,
): String =
    buildString {
        append("[msg")
        for (idPart in id) {
            append(Base64.getEncoder().encodeToString(idPart))
        }
        append("] ")
        append(data)
        if (buffers.isNotEmpty()) {
            append(" <contains ")
            append(buffers.size)
            append(" byte buffers>")
        }
    }

val MessageFormat =
    Json {
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
