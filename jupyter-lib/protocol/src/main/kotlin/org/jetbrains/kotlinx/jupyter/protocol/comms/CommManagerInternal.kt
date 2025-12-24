package org.jetbrains.kotlinx.jupyter.protocol.comms

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager

interface CommManagerInternal : CommManager {
    fun processCommOpen(
        commId: String,
        targetName: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ): Comm?

    fun processCommMessage(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    )

    fun processCommClose(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    )
}
