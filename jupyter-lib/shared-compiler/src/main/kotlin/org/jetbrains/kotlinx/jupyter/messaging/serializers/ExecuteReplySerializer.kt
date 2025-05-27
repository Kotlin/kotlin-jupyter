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
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteAbortReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteErrorReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteSuccessReply
import org.jetbrains.kotlinx.jupyter.messaging.MessageStatus

object ExecuteReplySerializer : KSerializer<ExecuteReply> {
    private val utilSerializer = serializer<JsonObject>()

    override val descriptor: SerialDescriptor
        get() = utilSerializer.descriptor

    override fun deserialize(decoder: Decoder): ExecuteReply {
        require(decoder is JsonDecoder)
        val json = decoder.decodeJsonElement().jsonObject
        val format = decoder.json

        val statusJson = json["status"] ?: throw SerializationException("Status field not present")

        val status = format.decodeFromJsonElement<MessageStatus>(statusJson)

        return when (status) {
            MessageStatus.OK -> format.decodeFromJsonElement<ExecuteSuccessReply>(json)
            MessageStatus.ERROR -> format.decodeFromJsonElement<ExecuteErrorReply>(json)
            MessageStatus.ABORT -> format.decodeFromJsonElement<ExecuteAbortReply>(json)
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: ExecuteReply,
    ) {
        when (value) {
            is ExecuteAbortReply -> encoder.encodeSerializableValue(serializer(), value)
            is ExecuteErrorReply -> encoder.encodeSerializableValue(serializer(), value)
            is ExecuteSuccessReply -> encoder.encodeSerializableValue(serializer(), value)
        }
    }
}
