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
 * Use [tryBind] on server sockets and [tryConnect] on client sockets.
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

    fun tryBind(): Boolean {
        if (!socket.bind(address)) return false
        if (socketType == SocketType.PUB) {
            // Classic slow-joiner workaround
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return true
    }

    fun tryConnect(): Boolean {
        if (socketType == SocketType.SUB) {
            if (!socket.subscribe(byteArrayOf())) return false
        }
        return socket.connect(address)
    }

    internal fun receiveMultipart(): List<ByteArray>? {
        val firstFrame: ByteArray =
            try {
                socket.recv(0, cancellationToken) ?: return null
            } catch (e: ZMQException) {
                if (e.errorCode == ZMQ.Error.EINTR.code || e.errorCode == ZError.ECANCELED) return null
                throw e
            }

        return buildList {
            add(firstFrame)
            while (socket.hasReceiveMore()) {
                add(socket.recv(0, cancellationToken))
            }
        }
    }

    /** Send the entire multipart message to the given socket (all-or-nothing semantics). */
    internal fun sendMultipart(frames: List<ByteArray>) {
        for ((i, frame) in frames.withIndex()) {
            val flags = if (i < frames.lastIndex) ZMQ.SNDMORE else 0
            socket.send(frame, flags)
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
