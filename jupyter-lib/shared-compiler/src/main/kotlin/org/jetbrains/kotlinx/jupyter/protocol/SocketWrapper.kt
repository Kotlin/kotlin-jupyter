package org.jetbrains.kotlinx.jupyter.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.zeromq.ZMQ
import java.security.SignatureException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

typealias SocketRawMessageCallback = JupyterSocket.(RawMessage) -> Unit

private val MESSAGE_DELIMITER: ByteArray = "<IDS|MSG>".map { it.code.toByte() }.toByteArray()
private val emptyJsonObjectString = Json.EMPTY.toString()
private val emptyJsonObjectStringBytes = emptyJsonObjectString.toByteArray()

fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

class SocketWrapper(
    val name: String,
    socket: ZMQ.Socket,
    private val address: String,
    private val hmac: HMAC,
) : SocketWithCancellationBase(socket), JupyterSocket {
    private val logger = getLogger(this::class.simpleName!!)
    private val lock = ReentrantLock()

    override fun bind(): Boolean {
        val res = bind(address)
        logger.debug("[$name] listen: $address")
        return res
    }

    override fun connect(): Boolean {
        val res = connect(address)
        logger.debug("[$name] connected: $address")
        return res
    }

    private val callbacks = mutableSetOf<SocketRawMessageCallback>()

    override fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback {
        callbacks.add(callback)
        return callback
    }

    override fun removeCallback(callback: SocketRawMessageCallback) {
        callbacks.remove(callback)
    }

    override fun onData(body: JupyterSocket.(ByteArray) -> Unit) = body(recv())

    override fun runCallbacksOnMessage() =
        receiveRawMessage()?.let { message ->
            callbacks.forEach { callback ->
                try {
                    callback(message)
                } catch (e: Throwable) {
                    logger.error("Exception thrown while processing a message", e)
                }
            }
        }

    override fun sendRawMessage(msg: RawMessage) {
        logger.debug("[{}] snd>: {}", name, msg)
        doSendRawMessage(msg)
    }

    private fun doSendRawMessage(msg: RawMessage) {
        assertNotCancelled()

        msg.id.forEach { sendMore(it) }
        sendMore(MESSAGE_DELIMITER)

        val properties = listOf(RawMessage::header, RawMessage::parentHeader, RawMessage::metadata, RawMessage::content)
        val signableMsg = properties.map { prop -> prop.get(msg)?.let { MessageFormat.encodeToString(it) }?.toByteArray() ?: emptyJsonObjectStringBytes }
        sendMore(hmac(signableMsg) ?: "")
        for (i in 0 until (signableMsg.size - 1)) {
            sendMore(signableMsg[i])
        }
        send(signableMsg.last())
    }

    override fun receiveRawMessage(): RawMessage? {
        return try {
            val msg = lock.withLock {
                doReceiveRawMessage()
            }
            logger.debug("[{}] >rcv: {}", name, msg)
            msg
        } catch (e: SignatureException) {
            logger.error("[$name] ${e.message}")
            null
        }
    }

    private fun doReceiveRawMessage(): RawMessage {
        assertNotCancelled()

        val ids = listOf(recv()) + generateSequence { recv() }.takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
        val sig = recvString().lowercase()
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

    override fun close() {
        callbacks.clear()
        super.close()
    }
}

fun createSocket(
    socketInfo: JupyterSocketInfo,
    context: ZMQ.Context,
    kernelConfig: KernelConfig,
    side: JupyterSocketSide,
): JupyterSocket {
    return SocketWrapper(
        socketInfo.name,
        context.socket(socketInfo.zmqType(side)),
        kernelConfig.addressForSocket(socketInfo),
        kernelConfig.hmac,
    )
}

fun KernelConfig.addressForSocket(socketInfo: JupyterSocketInfo): String {
    val port = ports[socketInfo.type]
    return "$transport://*:$port"
}

fun openServerSocket(
    socketInfo: JupyterSocketInfo,
    context: ZMQ.Context,
    kernelConfig: KernelConfig,
): JupyterSocket {
    return createSocket(socketInfo, context, kernelConfig, JupyterSocketSide.SERVER).apply {
        bind()
    }
}
