package org.jetbrains.kotlinx.jupyter.protocol

import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQException

abstract class SocketWithCancellationBase(
    private val socket: ZMQ.Socket,
) : SocketWithCancellation {
    private val cancellationToken: ZMQ.CancellationToken = socket.createCancellationToken()

    override fun recv() = socket.recv(0, cancellationToken) ?: throw ZMQException(
        "Unable to receive message",
        socket.errno(),
    )
    override fun recvString() = String(recv(), ZMQ.CHARSET)

    override fun sendMore(data: String) = socket.sendMore(data)
    override fun sendMore(data: ByteArray) = socket.sendMore(data)
    override fun send(data: ByteArray) = socket.send(data, 0, cancellationToken)
    override fun send(data: String): Boolean = send(data.toByteArray(ZMQ.CHARSET))

    override fun makeRelaxed() {
        socket.base().setSocketOpt(zmq.ZMQ.ZMQ_REQ_RELAXED, true)
    }

    override fun subscribe(topic: ByteArray) = socket.subscribe(topic)

    protected fun bind(address: String): Boolean {
        val res = socket.bind(address)
        if (socket.socketType == SocketType.PUB) {
            // Workaround to prevent losing few first messages on kernel startup
            // For more information on losing messages see this scheme:
            // http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
            // It seems we cannot do correct sync because messaging protocol
            // doesn't support this. Value of 500 ms was chosen experimentally.
            Thread.sleep(500)
        }
        return res
    }

    protected fun connect(address: String): Boolean {
        return socket.connect(address)
    }

    protected fun isCancelled() = cancellationToken.isCancellationRequested

    override fun close() {
        cancellationToken.cancel()
        socket.close()
    }
}
