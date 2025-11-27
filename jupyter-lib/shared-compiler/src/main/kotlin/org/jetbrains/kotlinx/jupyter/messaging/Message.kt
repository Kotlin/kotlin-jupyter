package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JUPYTER_PROTOCOL_VERSION
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageImpl
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.ZmqIdentities
import org.jetbrains.kotlinx.jupyter.protocol.buildMessageDebugString
import org.jetbrains.kotlinx.jupyter.protocol.data
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Message(
    val zmqIdentities: ZmqIdentities = emptyList(),
    val data: MessageData = MessageData(),
    val buffers: List<ByteArray> = emptyList(),
) {
    val type: MessageType
        get() = data.header!!.type

    val content: MessageContent
        get() = data.content!!

    override fun toString(): String =
        buildMessageDebugString(
            zmqIdentities,
            MessageFormat.encodeToString(data),
            buffers,
        )
}

fun RawMessage.toMessage(): Message =
    Message(
        zmqIdentities = zmqIdentities,
        data = MessageFormat.decodeFromJsonElement(data),
        buffers = buffers,
    )

fun Message.toRawMessage(): RawMessage =
    makeRawMessage(
        zmqIdentities = zmqIdentities,
        dataJson = MessageFormat.encodeToJsonElement<MessageData>(data).jsonObject,
        buffers = buffers,
    )

fun makeRawMessage(
    zmqIdentities: List<ByteArray>,
    dataJson: JsonObject,
    buffers: List<ByteArray> = emptyList(),
): RawMessage =
    RawMessageImpl(
        zmqIdentities = zmqIdentities,
        header = dataJson["header"]!!.jsonObject,
        parentHeader = dataJson["parent_header"] as? JsonObject,
        metadata = dataJson["metadata"] as? JsonObject,
        content = dataJson["content"]!!,
        buffers = buffers,
    )

fun makeReplyMessage(
    msg: RawMessage,
    msgType: MessageType? = null,
    sessionId: String? = null,
    header: MessageHeader? = null,
    parentHeader: MessageHeader? = null,
    metadata: JsonElement? = null,
    content: MessageContent? = null,
    buffers: List<ByteArray>? = null,
) = Message(
    zmqIdentities = msg.zmqIdentities,
    MessageData(
        header = header ?: makeHeader(msgType = msgType, incomingMsg = msg, sessionId = sessionId),
        parentHeader = parentHeader ?: MessageFormat.decodeFromJsonElement<MessageHeader>(msg.header),
        metadata = metadata,
        content = content,
    ),
    buffers = buffers ?: emptyList(),
)

fun makeHeader(
    msgType: MessageType? = null,
    incomingMsg: RawMessage? = null,
    sessionId: String? = null,
): MessageHeader {
    val parentHeader = incomingMsg?.header?.let { MessageFormat.decodeFromJsonElement<MessageHeader>(it) }
    return makeHeader(
        msgType ?: MessageType.NONE,
        parentHeader?.session ?: sessionId,
        parentHeader?.username ?: "kernel",
    )
}

private val ISO8601DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx'['VV']'")
val ISO8601DateNow: String get() = ZonedDateTime.now().format(ISO8601DateFormatter)

fun makeHeader(
    type: MessageType,
    sessionId: String?,
    username: String?,
): MessageHeader =
    MessageHeader(
        UUID.randomUUID().toString(),
        type,
        sessionId,
        username,
        JUPYTER_PROTOCOL_VERSION,
        ISO8601DateNow,
    )

fun JupyterSendSocket.sendMessage(msg: Message) {
    sendRawMessage(msg.toRawMessage())
}

@Throws(InterruptedException::class)
fun JupyterReceiveSocket.receiveMessage(): Message = receiveRawMessage().toMessage()
