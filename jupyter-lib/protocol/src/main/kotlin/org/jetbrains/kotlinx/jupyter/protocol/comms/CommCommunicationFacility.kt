package org.jetbrains.kotlinx.jupyter.protocol.comms

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage

interface CommCommunicationFacility {
    /**
     * If the cell is currently executing, contains the message that triggered the execution
     * (generally, of type `execute_request`).
     * Otherwise, `null`.
     *
     * Might be useful for i.e., output widget implementation
     */
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
