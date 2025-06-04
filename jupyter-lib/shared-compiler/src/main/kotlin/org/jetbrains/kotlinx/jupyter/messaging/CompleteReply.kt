package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// See https://jupyter-client.readthedocs.io/en/latest/messaging.html#completion
@Serializable
class CompleteReply(
    val matches: List<String>,
    @SerialName("cursor_start")
    val cursorStart: Int,
    @SerialName("cursor_end")
    val cursorEnd: Int,
    val paragraph: Paragraph,
    val metadata: Metadata,
) : OkReplyContent() {
    @Serializable
    class Metadata(
        @SerialName("_jupyter_types_experimental")
        val experimentalTypes: List<ExperimentalType>,
        @SerialName("_jupyter_extended_metadata")
        val extended: List<ExtendedMetadataEntry>,
    )

    @Serializable
    class ExperimentalType(
        val text: String,
        val type: String,
        val start: Int,
        val end: Int,
    )

    @Serializable
    class ExtendedMetadataEntry(
        val text: String,
        val displayText: String,
        val icon: String,
        val tail: String,
        val deprecation: String?,
    )
}
