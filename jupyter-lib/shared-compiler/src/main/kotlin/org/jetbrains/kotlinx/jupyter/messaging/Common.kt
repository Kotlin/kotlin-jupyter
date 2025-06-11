package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * See https://jupyter-client.readthedocs.io/en/latest/messaging.html#request-reply
 * for the definition of these values.
 */
@Serializable
enum class MessageStatus {
    @SerialName("ok")
    OK,

    @SerialName("error")
    ERROR,

    // This status has been deprecated since Jupyter 5.1 in favor of using ERROR
    // It should only be used for compatibility reasons.
    @SerialName("abort")
    ABORT,
}

/**
 * Top-level interface for the generic `content` part of a Jupyter message.
 * See https://jupyter-client.readthedocs.io/en/latest/messaging.html#content
 */
sealed interface MessageContent

/**
 * Top-level class for the content of "reply" messages.
 * This class is `abstract` with `status` as a constructor parameter so we can
 * serialize the `status` field. In an ideal world, this would be an interface instead.
 *
 * See https://jupyter-client.readthedocs.io/en/latest/messaging.html#request-reply
 */
@Serializable
sealed class MessageReplyContent(
    open val status: MessageStatus,
) : MessageContent

@Serializable
abstract class OkReplyContent : MessageReplyContent(MessageStatus.OK)

// @SerialName annotations are not inherited by subclasses, they must be manually added on every subclass
// They are just here for documentation purposes. See https://github.com/Kotlin/kotlinx.serialization/issues/2054
@Serializable
abstract class ErrorReplyContent : MessageReplyContent(MessageStatus.ERROR) {
    @SerialName("ename")
    abstract val name: String

    @SerialName("evalue")
    abstract val value: String
    abstract val traceback: List<String>
}

@Serializable
abstract class AbortReplyContent : MessageReplyContent(MessageStatus.ABORT)

/**
 * Data wrapper used by custom code completion events.
 */
@Serializable
class Paragraph(
    val cursor: Int,
    val text: String,
)
