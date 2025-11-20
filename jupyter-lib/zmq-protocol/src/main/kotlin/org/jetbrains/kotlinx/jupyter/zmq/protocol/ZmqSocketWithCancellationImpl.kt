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
    override fun recvMultipart(): Sequence<ByteArray> =
        sequence {
            do {
                yield(recv())
            } while (socket.hasReceiveMore())
        }

    override fun sendMultipart(message: Sequence<ByteArray>) {
        val iterator = message.iterator()
        while (iterator.hasNext()) {
            val frame = iterator.next()
            val flags = if (iterator.hasNext()) zmq.ZMQ.ZMQ_SNDMORE else 0
            send(frame, flags)
        }
    }

    @Throws(InterruptedException::class)
    private fun recv(): ByteArray {
        val result = cancellableOperation { socket.recv(0, cancellationToken) }
        return result ?: throw ZMQException(
            "Unable to receive message",
            socket.errno(),
        )
    }

    private fun send(
        data: ByteArray,
        flags: Int,
    ) {
        val isOk = cancellableOperation { socket.send(data, flags, cancellationToken) }
        if (!isOk) {
            throw ZMQException(
                "Unable to send message",
                socket.errno(),
            )
        }
    }

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
