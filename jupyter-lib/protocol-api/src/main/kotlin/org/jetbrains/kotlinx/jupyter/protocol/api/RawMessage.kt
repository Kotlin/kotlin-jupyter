package org.jetbrains.kotlinx.jupyter.protocol.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

typealias ZmqIdentities = List<ByteArray>

/**
 * Raw Jupyter message.
 *
 * This format reflects the format of the message described in Jupyter ZMQ wire protocol
 * (see https://jupyter-client.readthedocs.io/en/stable/messaging.html#the-wire-protocol).
 *
 * [zmqIdentities] parameter is used to create replies to this message in ZMQ protocol:
 * reply to this message should have the same [zmqIdentities].
 * This interface is also used for communicating via websocket protocol.
 * In this case, [zmqIdentities] should be just ignored.
 *
 * All other properties are applicable to each protocol and have the same format in each of them.
 */
interface RawMessage {
    val zmqIdentities: ZmqIdentities
    val header: JsonObject
    val parentHeader: JsonObject?
    val metadata: JsonObject?
    val content: JsonElement
    val buffers: List<ByteArray>
}

val RawMessage.type: String?
    get() {
        val type = header["msg_type"]
        if (type !is JsonPrimitive || !type.isString) return null
        return type.content
    }

val RawMessage.sessionId: String? get() = header["session"]?.jsonPrimitive?.content
val RawMessage.username: String? get() = header["username"]?.jsonPrimitive?.content
typealias RawMessageAction = (RawMessage) -> Unit
