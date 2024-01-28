package org.jetbrains.kotlinx.jupyter.messaging.comms

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.sendData
import org.jetbrains.kotlinx.jupyter.messaging.OpenDebugPortReply
import org.jetbrains.kotlinx.jupyter.messaging.ProvidedCommMessages.OPEN_DEBUG_PORT_TARGET
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

class DebugPortCommHandler : CommHandler {
    override val targetId: String
        get() = OPEN_DEBUG_PORT_TARGET

    override fun onReceive(comm: Comm, messageContent: JsonObject, repl: ReplForJupyter) {
        comm.sendData(OpenDebugPortReply(repl.debugPort))
    }
}
