package org.jetbrains.kotlinx.jupyter.protocol.comms

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage

interface CommCommunicationFacility {
    val contextMessage: RawMessage?

    fun sendCommOpen(
        commId: String,
        targetName: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    )

    fun sendCommMessage(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    )

    fun sendCommClose(
        commId: String,
        data: JsonObject,
        metadata: JsonElement? = null,
        buffers: List<ByteArray> = emptyList(),
    )

    fun processCallbacks(action: () -> Unit)
}
