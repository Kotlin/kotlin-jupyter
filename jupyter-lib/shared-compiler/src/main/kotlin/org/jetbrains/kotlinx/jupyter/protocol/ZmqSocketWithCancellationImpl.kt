package org.jetbrains.kotlinx.jupyter.protocol

import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQException

class ZmqSocketWithCancellationImpl(
    private val socket: ZMQ.Socket,
) : ZmqSocketWithCancellation {
    private val cancellationToken: ZMQ.CancellationToken = socket.createCancellationToken()

    @Throws(InterruptedException::class)
    override fun recv(): ByteArray {
        assertNotCancelled()

        val result =
            try {
                socket.recv(0, cancellationToken)
            } catch (e: ZMQException) {
                if (e.errorCode == ZMQ.Error.EINTR.code) {
                    throw InterruptedException()
                }
                throw e
            }

        return result ?: throw ZMQException(
            "Unable to receive message",
            socket.errno(),
        )
    }

    @Throws(InterruptedException::class)
    override fun recvString() = String(recv(), ZMQ.CHARSET)

    override fun sendMore(data: String): Boolean {
        assertNotCancelled()
        return socket.sendMore(data)
    }

    override fun sendMore(data: ByteArray): Boolean {
        assertNotCancelled()
        return socket.sendMore(data)
    }

    override fun send(data: ByteArray): Boolean {
        assertNotCancelled()
        return socket.send(data, 0, cancellationToken)
    }

    override fun send(data: String): Boolean = send(data.toByteArray(ZMQ.CHARSET))

    override fun makeRelaxed() {
        socket.base().setSocketOpt(zmq.ZMQ.ZMQ_REQ_RELAXED, true)
    }

    override fun subscribe(topic: ByteArray) = socket.subscribe(topic)

    internal fun bind(address: String): Boolean {
        val res = socket.bind(address)
        if (socket.socketType == SocketType.PUB) {
            // Workaround to prevent losing a few first messages on kernel startup
            // For more information on losing messages see this scheme:
            // http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
            // It seems we cannot do correct sync because messaging protocol
            // doesn't support this.
            // The value of 500 ms was chosen experimentally.
            Thread.sleep(500)
        }
        return res
    }

    internal fun connect(address: String): Boolean {
        return socket.connect(address)
    }

    internal fun assertNotCancelled() {
        if (isCancelled()) throw InterruptedException()
    }

    private fun isCancelled() = cancellationToken.isCancellationRequested

    override fun close() {
        try {
            cancellationToken.cancel()
        } finally {
            socket.close()
        }
    }
}
