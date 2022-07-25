package org.jetbrains.kotlinx.jupyter.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.zeromq.ZMQ
import java.security.SignatureException

private val MESSAGE_DELIMITER: ByteArray = "<IDS|MSG>".map { it.code.toByte() }.toByteArray()
private val emptyJsonObjectString = Json.EMPTY.toString()
private val emptyJsonObjectStringBytes = emptyJsonObjectString.toByteArray()

fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

fun ZMQ.Socket.sendRawMessage(msg: RawMessage, hmac: HMAC) {
    synchronized(this) {
        msg.id.forEach { sendMore(it) }
        sendMore(MESSAGE_DELIMITER)

        val properties = listOf(RawMessage::header, RawMessage::parentHeader, RawMessage::metadata, RawMessage::content)
        val signableMsg = properties.map { prop -> prop.get(msg)?.let { Json.encodeToString(it) }?.toByteArray() ?: emptyJsonObjectStringBytes }
        sendMore(hmac(signableMsg) ?: "")
        for (i in 0 until (signableMsg.size - 1)) {
            sendMore(signableMsg[i])
        }
        send(signableMsg.last())
    }
}

fun ZMQ.Socket.receiveRawMessage(start: ByteArray, hmac: HMAC): RawMessage {
    val ids = listOf(start) + generateSequence { recv() }.takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
    val sig = recvStr().lowercase()
    val header = recv()
    val parentHeader = recv()
    val metadata = recv()
    val content = recv()
    val calculatedSig = hmac(header, parentHeader, metadata, content)

    if (calculatedSig != null && sig != calculatedSig) {
        throw SignatureException("Invalid signature: expected $calculatedSig, received $sig - $ids")
    }

    fun ByteArray.parseJson(): JsonElement? {
        val json = Json.decodeFromString<JsonElement>(this.toString(Charsets.UTF_8))
        return if (json is JsonObject && json.isEmpty()) null else json
    }

    fun JsonElement?.orEmptyObject() = this ?: Json.EMPTY

    return RawMessageImpl(
        ids,
        header.parseJson()!!.jsonObject,
        parentHeader.parseJson()?.jsonObject,
        metadata.parseJson()?.jsonObject,
        content.parseJson().orEmptyObject(),
    )
}
