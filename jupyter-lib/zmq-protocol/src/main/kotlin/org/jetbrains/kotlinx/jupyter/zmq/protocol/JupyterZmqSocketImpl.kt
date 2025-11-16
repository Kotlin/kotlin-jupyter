package org.jetbrains.kotlinx.jupyter.zmq.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageImpl
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.LOCALHOST
import org.zeromq.ZMQ
import java.io.Closeable
import java.security.SignatureException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val MESSAGE_DELIMITER: ByteArray = "<IDS|MSG>".map { it.code.toByte() }.toByteArray()
private val emptyJsonObjectString = Json.EMPTY.toString()
private val emptyJsonObjectStringBytes = emptyJsonObjectString.toByteArray()
private val messagePartProperties =
    listOf(
        RawMessage::header,
        RawMessage::parentHeader,
        RawMessage::metadata,
        RawMessage::content,
    )

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

        zmqSocket.sendMultipart(
            sequence {
                for (idPart in msg.id) {
                    yield(idPart)
                }
                yield(MESSAGE_DELIMITER)

                val signableMessage =
                    messagePartProperties.map { prop ->
                        prop
                            .get(msg)
                            ?.let { MessageFormat.encodeToString(it) }
                            ?.toByteArray()
                            ?: emptyJsonObjectStringBytes
                    }

                yield(ZmqString.getBytes(hmac(signableMessage)))
                yieldAll(signableMessage)
                yieldAll(msg.buffers)
            },
        )
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

        val parts = zmqSocket.recvMultipart()
        val iter = parts.iterator()

        val ids =
            generateSequence { iter.next() }
                .takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
                .toList()
        val sig = ZmqString.getString(iter.next()).lowercase()
        val header = iter.next()
        val parentHeader = iter.next()
        val metadata = iter.next()
        val content = iter.next()
        val calculatedSig = hmac(header, parentHeader, metadata, content)

        if (sig != calculatedSig) {
            throw SignatureException("Invalid signature: expected $calculatedSig, received $sig - $ids")
        }

        fun ByteArray.parseJson(): JsonElement? {
            val json = Json.decodeFromString<JsonElement>(this.toString(Charsets.UTF_8))
            return if (json is JsonObject && json.isEmpty()) null else json
        }

        fun JsonElement?.orEmptyObject() = this ?: Json.EMPTY

        val buffers =
            buildList {
                iter.forEachRemaining { add(it) }
            }

        return RawMessageImpl(
            ids,
            header.parseJson()!!.jsonObject,
            parentHeader.parseJson()?.jsonObject,
            metadata.parseJson()?.jsonObject,
            content.parseJson().orEmptyObject(),
            buffers,
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
    configParams: KernelJupyterParams,
    side: JupyterSocketSide,
    hmac: HMAC,
    identity: ByteArray? = null,
): JupyterZmqSocket {
    val zmqSocket = context.socket(socketInfo.zmqType(side))
    zmqSocket.linger = 0

    identity?.let {
        zmqSocket.setIdentity(it)
    }

    return JupyterZmqSocketImpl(
        loggerFactory = loggerFactory,
        name = socketInfo.name,
        socket = zmqSocket,
        address = configParams.addressForZmqSocket(socketInfo, side),
        hmac = hmac,
    )
}

fun KernelJupyterParams.addressForZmqSocket(
    socketInfo: JupyterZmqSocketInfo,
    side: JupyterSocketSide,
): String {
    require(ports is ZmqKernelPorts) { "Wrong KernelAddress type" }
    val port = (ports as ZmqKernelPorts).ports.getValue(socketInfo.type)
    val host = host.takeUnless { it == ANY_HOST_NAME && side != JupyterSocketSide.SERVER } ?: LOCALHOST
    return "$transport://$host:$port"
}
