package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.script.experimental.api.ScriptDiagnostic

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
