package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import java.util.concurrent.ConcurrentHashMap

object MessageTypeSerializer : KSerializer<MessageType> {
    private val cache: MutableMap<String, MessageType> = ConcurrentHashMap()

    private fun getMessageType(type: String): MessageType =
        cache.computeIfAbsent(type) { newType ->
            MessageType.entries.firstOrNull { it.type == newType }
                ?: throw SerializationException("Unknown message type: $newType")
        }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            MessageType::class.qualifiedName!!,
            PrimitiveKind.STRING,
        )

    override fun deserialize(decoder: Decoder): MessageType = getMessageType(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: MessageType,
    ) {
        encoder.encodeString(value.type)
    }
}
