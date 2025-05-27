package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.Serializable

object ProvidedCommMessages {
    const val OPEN_DEBUG_PORT_TARGET: String = "open_debug_port_target"
}

@Serializable
class OpenDebugPortReply(
    val port: Int?,
) : OkReplyContent()
