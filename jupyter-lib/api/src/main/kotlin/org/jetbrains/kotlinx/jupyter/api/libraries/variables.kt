package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.util.ListToMapSerializer

@Serializable
data class Variable(val name: String, val value: String, val required: Boolean = false)
object VariablesMapSerializer : ListToMapSerializer<Variable, String, String>(
    serializer(),
    ::Variable,
    { it.name to it.value },
)

class DescriptorVariables(
    val properties: List<Variable> = listOf(),
    val hasOrder: Boolean = false,
)

object DescriptorVariablesSerializer : KSerializer<DescriptorVariables> {
    override val descriptor: SerialDescriptor
        get() = serializer<Any>().descriptor

    override fun deserialize(decoder: Decoder): DescriptorVariables {
        val hasOrder: Boolean
        val properties: List<Variable> = when (val obj = decoder.decodeSerializableValue(serializer<JsonElement>())) {
            is JsonArray -> {
                hasOrder = true
                Json.decodeFromJsonElement(obj)
            }
            is JsonObject -> {
                hasOrder = false
                Json.decodeFromJsonElement(VariablesMapSerializer, obj)
            }
            else -> throw SerializationException("Library descriptor should be either object or array")
        }
        return DescriptorVariables(properties, hasOrder)
    }

    override fun serialize(encoder: Encoder, value: DescriptorVariables) {
        encoder.encodeSerializableValue(serializer(), value.properties)
    }
}
