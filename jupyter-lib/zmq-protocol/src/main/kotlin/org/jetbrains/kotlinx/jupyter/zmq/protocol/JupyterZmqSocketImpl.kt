package org.jetbrains.kotlinx.jupyter.zmq.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageCallback
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
    socketData: ZmqSocketData,
    private val hmac: HMAC,
) : Closeable,
    JupyterZmqSocket {
    private val logger = loggerFactory.getLogger(this::class, socketData.name)

    private val zmqSocket =
        ZmqSocketWithCancellationImpl(
            loggerFactory,
            socketData,
        )

    override fun bind(): Boolean = zmqSocket.bind()

    override fun connect(): Boolean = zmqSocket.connect()

    override fun join() = zmqSocket.join()

    override fun sendRawMessage(msg: RawMessage) {
        doSendRawMessage(msg)
        logger.debug("snd>: {}", msg)
    }

    override fun sendBytes(message: List<ByteArray>) {
        zmqSocket.sendMultipart(message)
        logger.debug("snd bytes>: {} frames", message.size)
    }

    override fun onRawMessage(callback: RawMessageCallback) {
        onBytesReceived { bytes ->
            // Generally, there is exactly one callback,
            // so we won't do conversion multiple times
            val rawMessage = convertToRawMessage(bytes)
            if (rawMessage != null) {
                logger.debug(">rcv: {}", rawMessage)
                callback(rawMessage)
            }
        }
    }

    override fun onBytesReceived(callback: (List<ByteArray>) -> Unit) {
        zmqSocket.onReceive(callback)
    }

    override fun convertToRawMessage(zmqMessage: List<ByteArray>): RawMessage? {
        val iter = zmqMessage.iterator()

        val zmqIdentities =
            generateSequence { if (iter.hasNext()) iter.next() else null }
                .takeWhile { !it.contentEquals(MESSAGE_DELIMITER) }
                .toList()

        if (!iter.hasNext()) return null

        val sig = ZmqString.getString(iter.next()).lowercase()
        val blocks = messagePartProperties.map { iter.next() }
        val calculatedSig = hmac(blocks)

        if (sig != calculatedSig) {
            throw SignatureException("Invalid signature: expected $calculatedSig, received $sig for message $zmqIdentities")
        }

        fun ByteArray.parseJson(): JsonElement? {
            val json = Json.decodeFromString<JsonElement>(this.toString(Charsets.UTF_8))
            return if (json is JsonObject && json.isEmpty()) null else json
        }

        val buffers =
            buildList {
                iter.forEachRemaining { add(it) }
            }

        val blockJsons = blocks.map { it.parseJson()?.jsonObject }

        return RawMessageImpl(
            zmqIdentities = zmqIdentities,
            header = blockJsons[0] ?: error("There is no header in the message. Data was read: $blockJsons"),
            parentHeader = blockJsons[1],
            metadata = blockJsons[2],
            content = blockJsons[3] ?: Json.EMPTY,
            buffers = buffers,
        )
    }

    override fun close() {
        zmqSocket.close()
    }

    private fun doSendRawMessage(msg: RawMessage) {
        zmqSocket.sendMultipart(
            buildList {
                addAll(msg.zmqIdentities)
                add(MESSAGE_DELIMITER)

                val signableMessage =
                    messagePartProperties.map { prop ->
                        prop
                            .get(msg)
                            ?.let { MessageFormat.encodeToString(it).toByteArray() }
                            ?: emptyJsonObjectStringBytes
                    }

                add(ZmqString.getBytes(hmac(signableMessage)))
                addAll(signableMessage)
                addAll(msg.buffers)
            },
        )
    }
}

fun createZmqSocket(
    loggerFactory: KernelLoggerFactory,
    socketInfo: JupyterZmqSocketInfo,
    context: ZMQ.Context,
    configParams: KernelJupyterParams,
    side: JupyterSocketSide,
    hmac: HMAC,
    identity: ByteArray,
): JupyterZmqSocket {
    val socketData =
        ZmqSocketData(
            name = socketInfo.name + " on " + side.name.lowercase(),
            zmqContext = context,
            socketType = socketInfo.zmqType(side),
            socketIdentity = identity,
            address = configParams.addressForZmqSocket(socketInfo, side),
        )

    return JupyterZmqSocketImpl(
        loggerFactory = loggerFactory,
        socketData = socketData,
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
