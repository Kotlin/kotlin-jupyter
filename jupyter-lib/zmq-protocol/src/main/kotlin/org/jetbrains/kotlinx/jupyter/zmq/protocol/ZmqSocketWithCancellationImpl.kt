package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import zmq.ZError

class ZmqSocketWithCancellationImpl(
    private val socket: ZMQ.Socket,
) : ZmqSocketWithCancellation {
    private val cancellationToken: ZMQ.CancellationToken = socket.createCancellationToken()

    @Throws(InterruptedException::class)
    override fun recv(): ByteArray {
        val result = cancellableOperation { socket.recv(0, cancellationToken) }

        return result ?: throw ZMQException(
            "Unable to receive message",
            socket.errno(),
        )
    }

    @Throws(InterruptedException::class)
    override fun recvString() = String(recv(), ZMQ.CHARSET)

    override fun sendMore(data: String): Boolean = sendMore(data.toByteArray(ZMQ.CHARSET))

    override fun sendMore(data: ByteArray): Boolean = send(data, flags = zmq.ZMQ.ZMQ_SNDMORE)

    override fun send(data: String): Boolean = send(data.toByteArray(ZMQ.CHARSET))

    override fun send(data: ByteArray): Boolean = send(data, flags = 0)

    private fun send(
        data: ByteArray,
        flags: Int,
    ): Boolean = cancellableOperation { socket.send(data, flags, cancellationToken) }

    private inline fun <R> cancellableOperation(block: () -> R): R {
        assertNotCancelled()
        return try {
            block()
        } catch (e: Throwable) {
            // Sometimes, ZMQ fails with different exceptions (like NPE or ClosedSelectorException)
            // when the socket is closed during send/recv
            if (isCancelled() ||
                e is ZMQException &&
                (e.errorCode == ZMQ.Error.EINTR.code || e.errorCode == ZError.ECANCELED)
            ) {
                throw InterruptedException().apply { initCause(e) }
            }
            throw e
        }
    }

    override fun makeRelaxed() {
        socket.base().setSocketOpt(zmq.ZMQ.ZMQ_REQ_RELAXED, true)
    }

    override fun subscribe(topic: ByteArray) = socket.subscribe(topic)

    internal fun bind(address: String): Boolean {
        val res = socket.bind(address)
        if (socket.socketType == SocketType.PUB) {
            // Workaround to prevent losing a few first messages on kernel startup
            // For more information on losing messages, see this scheme:
            // http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
            // It seems we cannot do correct sync because messaging protocol
            // doesn't support this.
            // The value of 500 ms was chosen experimentally.
            Thread.sleep(500)
        }
        return res
    }

    internal fun connect(address: String): Boolean = socket.connect(address)

    internal fun assertNotCancelled() {
        if (isCancelled()) throw InterruptedException()
    }

    private fun isCancelled() = cancellationToken.isCancellationRequested

    override fun close() {
        tryFinally(
            action = { if (!isCancelled()) cancellationToken.cancel() },
            finally = { socket.close() },
        )
    }
}
