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
data class Variable(val name: String, val value: String, val ignored: Boolean = false)
object VariablesMapSerializer : ListToMapSerializer<Variable, String, String>(
    serializer(),
    ::Variable,
    { it.name to it.value },
)

data class DescriptorVariables(
    val properties: List<Variable> = listOf(),
    val hasOrder: Boolean = false,
)

fun DescriptorVariables.filter(predicate: (Variable) -> Boolean): DescriptorVariables {
    return DescriptorVariables(properties.filter(predicate), hasOrder)
}

object DescriptorVariablesSerializer : KSerializer<DescriptorVariables> {
    override val descriptor: SerialDescriptor
        get() = serializer<JsonElement>().descriptor

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
        }.filter { !it.ignored }

        return DescriptorVariables(properties, hasOrder)
    }

    override fun serialize(encoder: Encoder, value: DescriptorVariables) {
        if (value.hasOrder) {
            encoder.encodeSerializableValue(serializer(), value.properties)
        } else {
            encoder.encodeSerializableValue(serializer(), value.properties.associate { it.name to it.value })
        }
    }
}
