package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    @SerialName("ok")
    OK,

    @SerialName("error")
    ERROR,

    @SerialName("abort")
    ABORT;
}

@Serializable
abstract class MessageContent

@Serializable
abstract class MessageReplyContent(
    val status: MessageStatus,
) : MessageContent()

@Serializable
abstract class OkReply : MessageReplyContent(MessageStatus.OK)

@Serializable
class Paragraph(
    val cursor: Int,
    val text: String,
)
