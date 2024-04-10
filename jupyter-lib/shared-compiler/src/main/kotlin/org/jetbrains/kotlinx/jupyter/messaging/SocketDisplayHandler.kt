package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.containsDisplayId
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.withId
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.renderValue
import org.jetbrains.kotlinx.jupyter.util.EMPTY

class SocketDisplayHandler(
    private val communicationFacility: JupyterCommunicationFacility,
    private val notebook: MutableNotebook,
) : DisplayHandler {
    private val socket = communicationFacility.socketManager.iopub

    private fun sendMessage(
        type: MessageType,
        content: DisplayDataResponse,
    ) {
        val messageFactory = communicationFacility.messageFactory
        val message = messageFactory.makeReplyMessage(type, content = content)
        socket.sendMessage(message)
    }

    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = renderValue(notebook, host, value, id)?.let { if (id != null) it.withId(id) else it } ?: return
        val json = display.toJson(Json.EMPTY, null)

        notebook.currentCell?.addDisplay(display)

        val response: DisplayDataResponse = createResponse(json)
        sendMessage(MessageType.DISPLAY_DATA, response)
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = renderValue(notebook, host, value, id) ?: return
        val json = display.toJson(Json.EMPTY, null)
        if (id == null || !json.containsDisplayId(id)) {
            throw RuntimeException("`update_display_data` response should provide an id of data being updated")
        }

        val container = notebook.displays
        container.update(id, display)
        container.getById(id).distinctBy { it.cell.id }.forEach {
            it.cell.displays.update(id, display)
        }

        val response = createResponse(json)
        sendMessage(MessageType.UPDATE_DISPLAY_DATA, response)
    }

    private fun createResponse(json: JsonObject): DisplayDataResponse {
        val content =
            DisplayDataResponse(
                json["data"],
                json["metadata"],
                json["transient"],
            )
        return content
    }
}
