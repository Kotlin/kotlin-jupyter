package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path

/**
 * Serializer making it possible to send [kotlin.io.path.Path] values in Jupyter protocol messages.
 */
object PathSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Path,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Path = Path.of(decoder.decodeString())
}
