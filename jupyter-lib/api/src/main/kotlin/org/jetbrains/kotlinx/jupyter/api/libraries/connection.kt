package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.util.EMPTY

/**
 * Jupyter connection socket types
 * Here you can find an information about Jupyter sockets:
 * https://jupyter-client.readthedocs.io/en/stable/messaging.html#introduction
 *
 * For now, only adding callbacks for messages on `control` and `shell` sockets makes sense.
 */
enum class JupyterSocket {
    HB,
    SHELL,
    CONTROL,
    STDIN,
    IOPUB;
}

/**
 * Raw Jupyter message. [data] generally should contain `header`, `parent_header`, `content` and `metadata` fields
 *
 * @constructor Create empty Raw message
 */
interface RawMessage {
    val id: List<ByteArray>
    val data: JsonElement
}

val RawMessage.header: JsonObject?
    get() = (data as? JsonObject)?.get("header") as? JsonObject

val RawMessage.type: String?
    get() {
        val type = header?.get("msg_type")
        if (type !is JsonPrimitive || !type.isString) return null
        return type.content
    }

val RawMessage.content: JsonObject?
    get() = (data as? JsonObject)?.get("content") as? JsonObject

typealias CommOpenCallback = (Comm, JsonObject) -> Unit
typealias CommMsgCallback = (JsonObject) -> Unit
typealias CommCloseCallback = (JsonObject) -> Unit
typealias RawMessageAction = (RawMessage) -> Unit

/**
 * Callback for messages of type [messageType] coming to a certain [socket]
 */
interface RawMessageCallback {
    val socket: JupyterSocket
    val messageType: String
    val action: RawMessageAction
}

interface JupyterConnection {
    /**
     * Add callback for incoming message and return it
     */
    fun addMessageCallback(callback: RawMessageCallback): RawMessageCallback

    /**
     * Remove added message callback
     */
    fun removeMessageCallback(callback: RawMessageCallback)

    /**
     * Send raw [message] to a given [socketName]
     */
    fun send(socketName: JupyterSocket, message: RawMessage)

    /**
     * Send reply to a given [parentMessage] of type [type] to socket [socketName].
     * Simpler-to-use version of [send].
     */
    fun sendReply(socketName: JupyterSocket, parentMessage: RawMessage, type: String, content: JsonObject, metadata: JsonObject? = null)
}

interface CommManager {
    /**
     * Creates a comm with a given target, generates unique ID for it. Sends comm_open request to frontend
     *
     * @param target Target to create comm for. Should be registered on frontend side.
     * @param data Content of comm_open message
     * @return Created comm
     */
    fun openComm(target: String, data: JsonObject = Json.EMPTY): Comm

    /**
     * Closes a comm with a given ID. Sends comm_close request to frontend
     *
     * @param id ID of a comm to close
     * @param data Content of comm_close message
     */
    fun closeComm(id: String, data: JsonObject = Json.EMPTY)

    /**
     * Get all comms for a given target, or all opened comms if `target` is `null`
     */
    fun getComms(target: String? = null): Collection<Comm>

    /**
     * Register a [callback] for `comm_open` with a specified [target]. Overrides already registered callback.
     *
     * @param target
     * @param callback
     */
    fun registerCommTarget(target: String, callback: CommOpenCallback)

    /**
     * Unregister target callback
     */
    fun unregisterCommTarget(target: String)
}

interface Comm {
    /**
     * Comm target name
     */
    val target: String

    /**
     * Comm ID
     */
    val id: String

    /**
     * Send JSON data to this comm. Effectively sends `comm_msg` message to frontend
     */
    fun send(data: JsonObject)

    /**
     * Add [action] callback for `comm_msg` requests. Doesn't override existing callbacks
     *
     * @return Added callback
     */
    fun onMessage(action: CommMsgCallback): CommMsgCallback

    /**
     * Remove added [onMessage] callback
     */
    fun removeMessageCallback(callback: CommMsgCallback)

    /**
     * Closes a comm. Sends comm_close request to frontend if [notifyClient] is `true`
     */
    fun close(data: JsonObject = Json.EMPTY, notifyClient: Boolean = true)

    /**
     * Adds [action] callback for `comm_close` requests. Does not override existing callbacks
     */
    fun onClose(action: CommCloseCallback): CommCloseCallback

    /**
     * Remove added [onClose] callback
     */
    fun removeCloseCallback(callback: CommCloseCallback)
}

/**
 * Construct raw message callback
 */
fun rawMessageCallback(socket: JupyterSocket, messageType: String, action: RawMessageAction): RawMessageCallback {
    return object : RawMessageCallback {
        override val socket: JupyterSocket get() = socket
        override val messageType: String get() = messageType
        override val action: RawMessageAction get() = action
    }
}

/**
 * Send an object. `data` should be serializable to JSON object
 * (generally it means that the corresponding class should be marked with @Serializable)
 */
inline fun <reified T> Comm.sendData(data: T) {
    send(Json.encodeToJsonElement(data).jsonObject)
}

inline fun <reified T> Comm.onData(crossinline action: (T) -> Unit): CommMsgCallback {
    return onMessage { json ->
        val data = Json.decodeFromJsonElement<T>(json)
        action(data)
    }
}