package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.containsDisplayId
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.withIdIfNotNull
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.renderValue

class SocketDisplayHandler(
    private val communicationFacility: JupyterCommunicationFacility,
    private val notebook: MutableNotebook,
) : DisplayHandler {
    private val socket = communicationFacility.socketManager.iopub

    private fun sendMessage(
        type: MessageType,
        content: DisplayDataMessage,
    ) {
        val messageFactory = communicationFacility.messageFactory
        val message = messageFactory.makeReplyMessage(type, content = content)
        socket.sendMessage(message)
    }

    override fun render(
        value: Any?,
        host: ExecutionHost,
    ) = renderValue(notebook, host, value)

    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = render(value, host)?.withIdIfNotNull(id) ?: return
        val json = display.toJson(Json.EMPTY, null)

        notebook.currentCell?.addDisplay(display)

        val response: DisplayDataMessage = createResponse(json)
        flushStandardStreams()
        sendMessage(MessageType.DISPLAY_DATA, response)
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = render(value, host) ?: return
        val json = display.toJson(Json.EMPTY, id)
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

    override fun handleClearOutput(wait: Boolean) {
        val content = ClearOutputMessage(wait)
        val message =
            communicationFacility.messageFactory
                .makeReplyMessage(MessageType.CLEAR_OUTPUT, content = content)
        socket.sendMessage(message)
    }

    private fun createResponse(json: JsonObject): DisplayDataMessage {
        val content =
            DisplayDataMessage(
                json["data"],
                json["metadata"],
                json["transient"],
            )
        return content
    }

    /**
     * Flushing of the system streams will automatically
     * send stream messages to the frontend.
     * We need to do it before sending display messages to ensure
     * correct messages order.
     */
    private fun flushStandardStreams() {
        System.out.flush()
        System.err.flush()
    }
}
