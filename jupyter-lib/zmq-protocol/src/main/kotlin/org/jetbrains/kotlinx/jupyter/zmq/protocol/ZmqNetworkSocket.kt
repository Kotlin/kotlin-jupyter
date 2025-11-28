package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import zmq.ZError
import java.io.Closeable

/**
 * Simple wrapper around [ZMQ.Socket] that encapsulates cancellation and
 * socket type differences.
 * Use [bind] on server sockets and [connect] on client sockets.
 *
 * This class is not thread-safe.
 * [receiveMultipart] and [sendMultipart] should not be called concurrently.
 */
internal class ZmqNetworkSocket(
    socketData: ZmqSocketData,
) : Closeable {
    val address: String = socketData.address
    private val socketType: SocketType = socketData.socketType

    val socket: ZMQ.Socket = socketData.createSocket()
    private val cancellationToken = socket.createCancellationToken()

    private val socketName = socketData.name

    fun bind() {
        if (!socket.bind(address)) {
            throw ZMQException("Failed to bind (socket: $socketName)", socket.errno())
        }
        if (socketType == SocketType.PUB) {
            // Classic slow-joiner workaround
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                // ignore
            }
        }
    }

    fun connect() {
        if (socketType == SocketType.SUB) {
            if (!socket.subscribe(byteArrayOf())) {
                throw ZMQException("Failed to subscribe (socket: $socketName)", socket.errno())
            }
        }
        if (!socket.connect(address)) {
            throw ZMQException("Failed to connect (socket: $socketName)", socket.errno())
        }
    }

    fun receiveMultipart(): List<ByteArray>? {
        val firstFrame: ByteArray = nullOnCancellation { socket.recv(0, cancellationToken) } ?: return null

        return buildList {
            add(firstFrame)
            while (socket.hasReceiveMore()) {
                add(nullOnCancellation { socket.recv(0, cancellationToken) } ?: return null)
            }
        }
    }

    /** Send the entire multipart message to the given socket (all-or-nothing semantics). */
    fun sendMultipart(frames: List<ByteArray>) {
        nullOnCancellation {
            for ((i, frame) in frames.withIndex()) {
                val flags = if (i < frames.lastIndex) ZMQ.SNDMORE else 0
                socket.send(frame, flags, cancellationToken)
            }
        }
    }

    private inline fun <T : Any> nullOnCancellation(block: () -> T): T? =
        try {
            block()
        } catch (e: ZMQException) {
            if (e.errorCode == ZMQ.Error.EINTR.code || e.errorCode == ZError.ECANCELED) {
                null
            } else {
                throw e
            }
        }

    @Synchronized
    override fun close() {
        mergeExceptions {
            catchIndependently {
                if (!cancellationToken.isCancellationRequested) {
                    cancellationToken.cancel()
                }
            }
            catchIndependently { socket.close() }
        }
    }
}
