package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.messaging.MessageStatus
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataErrorReply
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataReply
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataSuccessReply

object UpdateClientMetadataReplySerializer : KSerializer<UpdateClientMetadataReply> {
    private val utilSerializer = serializer<JsonObject>()

    override val descriptor: SerialDescriptor
        get() = utilSerializer.descriptor

    override fun deserialize(decoder: Decoder): UpdateClientMetadataReply {
        require(decoder is JsonDecoder)
        val json = decoder.decodeJsonElement().jsonObject
        val format = decoder.json

        val statusJson = json["status"] ?: throw SerializationException("Status field not present")

        val status = format.decodeFromJsonElement<MessageStatus>(statusJson)

        return when (status) {
            MessageStatus.OK -> format.decodeFromJsonElement<UpdateClientMetadataSuccessReply>(json)
            MessageStatus.ERROR -> format.decodeFromJsonElement<UpdateClientMetadataErrorReply>(json)
            MessageStatus.ABORT -> error("Message status not supported: $status")
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: UpdateClientMetadataReply,
    ) {
        when (value) {
            is UpdateClientMetadataErrorReply -> encoder.encodeSerializableValue(serializer(), value)
            is UpdateClientMetadataSuccessReply -> encoder.encodeSerializableValue(serializer(), value)
        }
    }
}
