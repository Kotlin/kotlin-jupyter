package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.kotlinx.jupyter.messaging.DetailLevel
import java.util.concurrent.ConcurrentHashMap

object DetailsLevelSerializer : KSerializer<DetailLevel> {
    private val cache: MutableMap<Int, DetailLevel> = ConcurrentHashMap()

    private fun getDetailsLevel(type: Int): DetailLevel =
        cache.computeIfAbsent(type) { newLevel ->
            DetailLevel.entries.firstOrNull { it.level == newLevel }
                ?: throw SerializationException("Unknown details level: $newLevel")
        }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            DetailLevel::class.qualifiedName!!,
            PrimitiveKind.INT,
        )

    override fun deserialize(decoder: Decoder): DetailLevel = getDetailsLevel(decoder.decodeInt())

    override fun serialize(
        encoder: Encoder,
        value: DetailLevel,
    ) {
        encoder.encodeInt(value.level)
    }
}
