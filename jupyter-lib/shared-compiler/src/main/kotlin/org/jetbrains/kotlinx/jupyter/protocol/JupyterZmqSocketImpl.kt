package org.jetbrains.kotlinx.jupyter.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.startup.ZmqKernelPorts
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.slf4j.Logger
import org.zeromq.ZMQ
import java.io.Closeable
import java.security.SignatureException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val MESSAGE_DELIMITER: ByteArray = "<IDS|MSG>".map { it.code.toByte() }.toByteArray()
private val emptyJsonObjectString = Json.EMPTY.toString()
private val emptyJsonObjectStringBytes = emptyJsonObjectString.toByteArray()

fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

fun interface RawMessageCallback {
    @Throws(InterruptedException::class)
    operator fun invoke(msg: RawMessage)
}

class CallbackHandler(
    private val logger: Logger,
) : Closeable {
    private val callbacks = mutableSetOf<RawMessageCallback>()

    fun addCallback(callback: RawMessageCallback) {
        callbacks.add(callback)
    }

    @Throws(InterruptedException::class)
    fun runCallbacks(payload: RawMessage) {
        callbacks.forEach { callback ->
            try {
                callback(payload)
            } catch (e: Throwable) {
                if (e is InterruptedException) {
                    throw e
                }
                logger.error("Exception thrown while processing a message", e)
            }
        }
    }

    override fun close() {
        callbacks.clear()
    }
}

class JupyterZmqSocketImpl(
    loggerFactory: KernelLoggerFactory,
    val name: String,
    socket: ZMQ.Socket,
    private val address: String,
    private val hmac: HMAC,
) : Closeable,
    JupyterZmqSocket {
    private val logger = loggerFactory.getLogger(this::class)
    private val lock = ReentrantLock()

    override val zmqSocket = ZmqSocketWithCancellationImpl(socket)

    override fun bind(): Boolean {
        val res = zmqSocket.bind(address)
        logger.debug("[$name] listen: $address")
        return res
    }

    override fun connect(): Boolean {
        val res = zmqSocket.connect(address)
        logger.debug("[$name] connected: $address")
        return res
    }

    override fun sendRawMessage(msg: RawMessage) {
        logger.debug("[{}] snd>: {}", name, msg)
        doSendRawMessage(msg)
    }

    private fun doSendRawMessage(msg: RawMessage) {
        zmqSocket.assertNotCancelled()

        msg.id.forEach { zmqSocket.sendMore(it) }
        zmqSocket.sendMore(MESSAGE_DELIMITER)

        val properties = listOf(RawMessage::header, RawMessage::parentHeader, RawMessage::metadata, RawMessage::content)
        val signableMsg =
            properties.map { prop ->
                prop.get(msg)?.let { MessageFormat.encodeToString(it) }?.toByteArray() ?: emptyJsonObjectStringBytes
            }
        zmqSocket.sendMore(hmac(signableMsg))
        for (i in 0 until (signableMsg.size - 1)) {
            zmqSocket.sendMore(signableMsg[i])
        }
        zmqSocket.send(signableMsg.last())
    }

    @Throws(InterruptedException::class)
    override fun receiveRawMessage(): RawMessage? =
        try {
            val msg =
                lock.withLock {
                    doReceiveRawMessage()
                }
            logger.debug("[{}] >rcv: {}", name, msg)
            msg
        } catch (e: SignatureException) {
            logger.error("[$name] ${e.message}")
            null
        }

    @Throws(InterruptedException::class)
    private fun doReceiveRawMessage(): RawMessage {
        zmqSocket.assertNotCancelled()

        val ids =
            listOf(zmqSocket.recv()) +
                generateSequence { zmqSocket.recv() }.takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
        val sig = zmqSocket.recvString().lowercase()
        val header = zmqSocket.recv()
        val parentHeader = zmqSocket.recv()
        val metadata = zmqSocket.recv()
        val content = zmqSocket.recv()
        val calculatedSig = hmac(header, parentHeader, metadata, content)

        if (sig != calculatedSig) {
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
        zmqSocket.close()
    }
}

fun createZmqSocket(
    loggerFactory: KernelLoggerFactory,
    socketInfo: JupyterZmqSocketInfo,
    context: ZMQ.Context,
    kernelConfig: KernelConfig,
    side: JupyterSocketSide,
): JupyterZmqSocket {
    val zmqSocket = context.socket(socketInfo.zmqType(side))
    zmqSocket.linger = 0

    val jupyterParams = kernelConfig.jupyterParams
    return JupyterZmqSocketImpl(
        loggerFactory = loggerFactory,
        name = socketInfo.name,
        socket = zmqSocket,
        address = jupyterParams.addressForZmqSocket(socketInfo),
        hmac = jupyterParams.hmac,
    )
}

fun KernelJupyterParams.addressForZmqSocket(socketInfo: JupyterZmqSocketInfo): String {
    require(ports is ZmqKernelPorts) { "Wrong KernelAddress type" }
    val port = ports.ports.getValue(socketInfo.type)
    return "$transport://$host:$port"
}
