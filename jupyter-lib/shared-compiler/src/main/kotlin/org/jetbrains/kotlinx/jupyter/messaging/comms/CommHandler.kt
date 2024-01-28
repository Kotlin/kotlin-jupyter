package org.jetbrains.kotlinx.jupyter.messaging.comms

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

interface CommHandler {
    val targetId: String

    fun onReceive(comm: Comm, messageContent: JsonObject, repl: ReplForJupyter)
}

fun ReplForJupyter.installCommHandler(commHandler: CommHandler) {
    val repl = this
    notebook.commManager.registerCommTarget(commHandler.targetId) { comm, _ ->
        // handler.onReceive(comm, data, repl) // maybe send right away?

        comm.onMessage {
            commHandler.onReceive(comm, it, repl)
        }
    }
}

fun Collection<CommHandler>.requireUniqueTargets() {
    val commHandlers = this
    val uniqueTargets = commHandlers.distinctBy { it.targetId }.size
    require(uniqueTargets == commHandlers.size) {
        val duplicates = commHandlers.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        "Duplicated bundled comm targets found! $duplicates"
    }
}
