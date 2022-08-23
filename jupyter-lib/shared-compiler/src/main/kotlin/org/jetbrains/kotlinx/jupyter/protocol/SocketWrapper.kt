package org.jetbrains.kotlinx.jupyter.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.security.SignatureException

typealias SocketRawMessageCallback = JupyterSocket.(RawMessage) -> Unit

class SocketWrapper(
    private val socketInfo: JupyterSocketInfo,
    override val socket: ZMQ.Socket,
    private val hmac: HMAC,
    private val kernelConfig: KernelConfig,
) : JupyterSocket {
    private val logger = getLogger(this::class.simpleName!!)

    val name: String get() = socketInfo.name

    private val address: String get() {
        val port = kernelConfig.ports[socketInfo.type]
        return "${kernelConfig.transport}://*:$port"
    }

    override fun bind(): Boolean {
        val res = socket.bind(address)
        if (socket.socketType == SocketType.PUB) {
            // Workaround to prevent losing few first messages on kernel startup
            // For more information on losing messages see this scheme:
            // http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
            // It seems we cannot do correct sync because messaging protocol
            // doesn't support this. Value of 500 ms was chosen experimentally.
            Thread.sleep(500)
        }
        logger.debug("[$name] listen: $address")
        return res
    }

    override fun connect(): Boolean {
        return socket.connect(address)
    }

    private val callbacks = mutableSetOf<SocketRawMessageCallback>()

    override fun onRawMessage(callback: SocketRawMessageCallback): SocketRawMessageCallback {
        callbacks.add(callback)
        return callback
    }

    override fun removeCallback(callback: SocketRawMessageCallback) {
        callbacks.remove(callback)
    }

    override fun onData(body: JupyterSocket.(ByteArray) -> Unit) = socket.recv()?.let { body(it) }

    override fun runCallbacksOnMessage() = socket.recv()?.let { bytes ->
        receiveRawMessage(bytes)?.let { message ->
            callbacks.forEach { callback ->
                try {
                    callback(message)
                } catch (e: Throwable) {
                    logger.error("Exception thrown while processing a message", e)
                }
            }
        }
    }

    override fun sendRawMessage(msg: RawMessage) {
        logger.debug("[$name] snd>: $msg")
        socket.sendRawMessage(msg, hmac)
    }

    override fun receiveRawMessage(start: ByteArray): RawMessage? {
        return try {
            val msg = socket.receiveRawMessage(start, hmac)
            logger.debug("[$name] >rcv: $msg")
            msg
        } catch (e: SignatureException) {
            logger.error("[$name] ${e.message}")
            null
        }
    }

    override fun close() {
        callbacks.clear()
        socket.close()
    }
}

fun createSocket(
    socketInfo: JupyterSocketInfo,
    context: ZMQ.Context,
    hmac: HMAC,
    kernelConfig: KernelConfig,
    side: JupyterSocketSide,
): JupyterSocket {
    return SocketWrapper(
        socketInfo,
        context.socket(socketInfo.zmqType(side)),
        hmac,
        kernelConfig
    )
}

fun openServerSocket(
    socketInfo: JupyterSocketInfo,
    context: ZMQ.Context,
    hmac: HMAC,
    kernelConfig: KernelConfig,
): JupyterSocket {
    return createSocket(socketInfo, context, hmac, kernelConfig, JupyterSocketSide.SERVER).apply {
        bind()
    }
}
