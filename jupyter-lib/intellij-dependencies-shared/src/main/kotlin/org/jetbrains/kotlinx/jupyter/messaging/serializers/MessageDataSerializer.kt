package org.jetbrains.kotlinx.jupyter.messaging.serializers

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.messaging.MessageContent
import org.jetbrains.kotlinx.jupyter.messaging.MessageData
import org.jetbrains.kotlinx.jupyter.messaging.MessageHeader
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.protocol.messageDataJson
import kotlin.reflect.full.createType

object MessageDataSerializer : KSerializer<MessageData> {
    @InternalSerializationApi
    private val contentSerializers = MessageType.entries.associate { it.type to it.contentClass.serializer() }

    override val descriptor: SerialDescriptor = serializer<JsonObject>().descriptor

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(decoder: Decoder): MessageData {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val format = decoder.json

        val header = element["header"]?.let { format.decodeFromJsonElement<MessageHeader?>(it) }
        val parentHeader = element["parent_header"]?.let { format.decodeFromJsonElement<MessageHeader?>(it) }
        val metadata = element["metadata"]?.let { format.decodeFromJsonElement<JsonElement?>(it) }

        val content =
            if (header != null) {
                val contentSerializer = chooseSerializer(header.type)
                element["content"]?.let {
                    format.decodeFromJsonElement(contentSerializer, it)
                }
            } else {
                null
            }

        return MessageData(header, parentHeader, metadata, content)
    }

    override fun serialize(
        encoder: Encoder,
        value: MessageData,
    ) {
        require(encoder is JsonEncoder)
        val format = encoder.json

        val content =
            value.content?.let {
                format.encodeToJsonElement(serializer(it::class.createType()), it)
            } ?: Json.EMPTY

        encoder.encodeJsonElement(
            messageDataJson(
                format.encodeToJsonElement(value.header).jsonObject,
                value.parentHeader?.let { format.encodeToJsonElement(it) }?.jsonObject,
                value.metadata?.let { format.encodeToJsonElement(it) }?.jsonObject,
                content,
            ),
        )
    }

    @InternalSerializationApi
    private fun chooseSerializer(messageType: MessageType): KSerializer<out MessageContent> =
        contentSerializers[messageType.type] ?: throw ReplException("Unknown message type: $messageType")
}
