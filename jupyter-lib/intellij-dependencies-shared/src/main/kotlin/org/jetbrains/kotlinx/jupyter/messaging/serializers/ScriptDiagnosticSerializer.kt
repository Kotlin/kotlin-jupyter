package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode

object ScriptDiagnosticSerializer : KSerializer<ScriptDiagnostic> {
    override val descriptor: SerialDescriptor
        get() = JsonObject.serializer().descriptor

    /**
     * Deserialization produces values only suitable for serializing them back and sending them to Jupyter Web.
     * These are not proper [ScriptDiagnostic] values.
     */
    override fun deserialize(decoder: Decoder): ScriptDiagnostic {
        require(decoder is JsonDecoder)

        val diagnosticJson = decoder.decodeJsonElement().jsonObject
        return ScriptDiagnostic(
            code = -1, // placeholder value
            message = diagnosticJson.getValue("message").jsonPrimitive.content,
            severity = ScriptDiagnostic.Severity.valueOf(diagnosticJson.getValue("severity").jsonPrimitive.content),
            location =
                diagnosticJson["loc"]?.jsonObject?.let {
                    SourceCode.Location(
                        start =
                            SourceCode.Position(
                                line =
                                    it
                                        .getValue("start")
                                        .jsonObject
                                        .getValue("line")
                                        .jsonPrimitive.int,
                                col =
                                    it
                                        .getValue("start")
                                        .jsonObject
                                        .getValue("col")
                                        .jsonPrimitive.int,
                            ),
                        end =
                            it["end"]?.jsonObject?.let { end ->
                                SourceCode.Position(
                                    line = end.getValue("line").jsonPrimitive.int,
                                    col = end.getValue("col").jsonPrimitive.int,
                                )
                            },
                    )
                },
        )
    }

    /**
     * Serialization produces values only suitable for sending them to Jupyter Web. Some information is lost.
     */
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
