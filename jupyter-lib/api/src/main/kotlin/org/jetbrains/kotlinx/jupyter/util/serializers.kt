package org.jetbrains.kotlinx.jupyter.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.api.ExactRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.ResultHandlerCodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceFallbacksBundle
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

private val emptyJsonObject = JsonObject(mapOf())

@Suppress("unused")
val Json.EMPTY get() = emptyJsonObject

abstract class PrimitiveStringPropertySerializer<T : Any>(
    kClass: KClass<T>,
    private val prop: KProperty1<T, String>,
    private val ctr: (String) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(kClass.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        val p = decoder.decodeString()
        return ctr(p)
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(prop.get(value))
    }
}

object CodeExecutionSerializer : PrimitiveStringPropertySerializer<CodeExecution>(
    CodeExecution::class,
    CodeExecution::code,
    ::CodeExecution,
)

object TypeHandlerCodeExecutionSerializer : PrimitiveStringPropertySerializer<ResultHandlerCodeExecution>(
    ResultHandlerCodeExecution::class,
    ResultHandlerCodeExecution::code,
    ::ResultHandlerCodeExecution,
)

abstract class ListToMapSerializer<T, K, V>(
    private val utilSerializer: KSerializer<Map<K, V>>,
    private val mapper: (K, V) -> T,
    private val reverseMapper: (T) -> Pair<K, V>,
) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor
        get() = utilSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val tempMap = utilSerializer.deserialize(decoder)
        return tempMap.map { (key, value) -> mapper(key, value) }
    }

    override fun serialize(encoder: Encoder, value: List<T>) {
        val tempMap = value.associate(reverseMapper)
        utilSerializer.serialize(encoder, tempMap)
    }
}

object RenderersSerializer : ListToMapSerializer<ExactRendererTypeHandler, String, ResultHandlerCodeExecution>(
    serializer(),
    ::ExactRendererTypeHandler,
    { it.className to it.execution },
)

object KotlinKernelVersionSerializer : KSerializer<KotlinKernelVersion> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(KotlinKernelVersion::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): KotlinKernelVersion {
        val str = decoder.decodeString()
        return KotlinKernelVersion.from(str) ?: throw SerializationException("Wrong format of kotlin kernel version: $str")
    }

    override fun serialize(encoder: Encoder, value: KotlinKernelVersion) {
        encoder.encodeString(value.toString())
    }
}

object ResourceBunchSerializer : KSerializer<ResourceFallbacksBundle> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(ResourceFallbacksBundle::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ResourceFallbacksBundle {
        return when (val obj = decoder.decodeSerializableValue(serializer<JsonElement>())) {
            is JsonArray -> {
                ResourceFallbacksBundle(
                    obj.map {
                        Json.decodeFromJsonElement(it)
                    },
                )
            }
            is JsonObject -> {
                ResourceFallbacksBundle(
                    listOf(
                        Json.decodeFromJsonElement(obj),
                    ),
                )
            }
            else -> throw SerializationException("Wrong representation for resource location")
        }
    }

    override fun serialize(encoder: Encoder, value: ResourceFallbacksBundle) {
        encoder.encodeSerializableValue(serializer(), value.locations)
    }
}

object PatternNameAcceptanceRuleSerializer : KSerializer<PatternNameAcceptanceRule> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(PatternNameAcceptanceRule::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PatternNameAcceptanceRule {
        val rule = decoder.decodeString()
        fun throwError(): Nothing = throw SerializationException("Wrong format of pattern rule: $rule")

        val parts = rule.split(':').map { it.trim() }
        val (sign, pattern) = when (parts.size) {
            1 -> "+" to parts[0]
            2 -> parts[0] to parts[1]
            else -> throwError()
        }
        val accepts = when (sign) {
            "+" -> true
            "-" -> false
            else -> throwError()
        }

        return PatternNameAcceptanceRule(accepts, pattern)
    }

    override fun serialize(encoder: Encoder, value: PatternNameAcceptanceRule) {
        encoder.encodeString("${ if (value.acceptsFlag) '+' else '-' }:${value.pattern}")
    }
}
