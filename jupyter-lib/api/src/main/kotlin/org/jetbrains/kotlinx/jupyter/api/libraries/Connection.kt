package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY

typealias CommOpenCallback = (Comm, JsonObject) -> Unit
typealias CommMsgCallback = (JsonObject) -> Unit
typealias CommCloseCallback = (JsonObject) -> Unit

/**
 * Manages custom messages in the notebook, for more info see
 * https://jupyter-client.readthedocs.io/en/latest/messaging.html#custom-messages
 */
interface CommManager {
    /**
     * Creates a comm with a given target, generates unique ID for it. Sends comm_open request to frontend
     *
     * @param target Target to create comm for. Should be registered on frontend side.
     * @param data Content of comm_open message
     * @return Created comm
     */
    fun openComm(
        target: String,
        data: JsonObject = Json.EMPTY,
    ): Comm

    /**
     * Closes a comm with a given ID. Sends comm_close request to frontend
     *
     * @param id ID of a comm to close
     * @param data Content of comm_close message
     */
    fun closeComm(
        id: String,
        data: JsonObject = Json.EMPTY,
    )

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
    fun registerCommTarget(
        target: String,
        callback: CommOpenCallback,
    )

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
    fun close(
        data: JsonObject = Json.EMPTY,
        notifyClient: Boolean = true,
    )

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
 * Send an object. `data` should be serializable to JSON object
 * (generally it means that the corresponding class should be marked with @Serializable)
 */
inline fun <reified T> Comm.sendData(data: T) {
    send(Json.encodeToJsonElement(data).jsonObject)
}

inline fun <reified T> Comm.onData(crossinline action: (T) -> Unit): CommMsgCallback =
    onMessage { json ->
        val data = Json.decodeFromJsonElement<T>(json)
        action(data)
    }
