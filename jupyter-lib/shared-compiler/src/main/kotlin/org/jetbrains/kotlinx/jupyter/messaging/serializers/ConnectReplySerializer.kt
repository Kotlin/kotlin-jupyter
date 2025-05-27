package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.messaging.ConnectReply

object ConnectReplySerializer : KSerializer<ConnectReply> {
    private val jsonSerializer = JsonObject.serializer()

    override val descriptor: SerialDescriptor = jsonSerializer.descriptor

    override fun deserialize(decoder: Decoder): ConnectReply {
        val json = decoder.decodeSerializableValue(jsonSerializer)
        return ConnectReply(json)
    }

    override fun serialize(
        encoder: Encoder,
        value: ConnectReply,
    ) {
        encoder.encodeSerializableValue(jsonSerializer, value.ports)
    }
}
