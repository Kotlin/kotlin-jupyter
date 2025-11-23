package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.catchAllIndependentlyAndMerge
import org.zeromq.ZMQ
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Thread-safe wrapper around a ZeroMQ socket that allows calling send/recv from different threads.
 *
 * Design:
 * - A single [routerThread] owns the real [networkSocket] (the one bound or connected to TCP/IPC/etc).
 * - Other threads never touch the network socket directly; they interact via:
 *   - A blocking queue [sendQueue] for outgoing messages
 *   - A blocking queue [receiveQueue] for incoming messages
 */
internal class ZmqSocketWithCancellationImpl(
    loggerFactory: KernelLoggerFactory,
    socketData: ZmqSocketData,
) : Closeable {
    private val logger = loggerFactory.getLogger(this::class, socketData.name)

    private val networkSocket = ZmqNetworkSocket(socketData)

    private val poller = socketData.zmqContext.poller(2)
    private val sendQueue = BlockingSignallingQueue<List<ByteArray>>(MESSAGE_QUEUE_CAPACITY)
    private val receiveQueue = ArrayBlockingQueue<List<ByteArray>>(MESSAGE_QUEUE_CAPACITY)

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    // Router worker thread (the only thread that touches the ZMQ socket)
    private val routerThread: Thread =
        thread(
            start = false,
            name = "${socketData.name}-router-thread",
            block = ::processMessages,
        )

    @Synchronized
    internal fun bind(): Boolean {
        val isBound = networkSocket.bind()
        start()
        logger.debug("listening on ${networkSocket.address}")
        return isBound
    }

    @Synchronized
    internal fun connect(): Boolean {
        val isConnected = networkSocket.connect()
        start()
        logger.debug("connected to ${networkSocket.address}")
        return isConnected
    }

    internal fun sendMultipart(message: List<ByteArray>) {
        assertNotCancelled()
        sendQueue.put(message)
    }

    internal fun receiveMultipart(): List<ByteArray> {
        assertNotCancelled()
        return receiveQueue.take()
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        catchAllIndependentlyAndMerge(
            { networkSocket.close() },
            { sendQueue.close() },
            { poller.close() },
            { routerThread.interrupt() },
            { receiveQueue.clear() },
        )
    }

    /** Starts the router thread once (idempotent), after bind/connect. */
    @Synchronized
    private fun start() {
        if (!started.compareAndSet(false, true)) return
        assertNotCancelled()
        routerThread.start()
    }

    private fun processMessages() {
        // Register both the ZMQ socket and the SelectableChannel
        poller.register(networkSocket.socket, ZMQ.Poller.POLLIN)
        poller.register(sendQueue.signaller, ZMQ.Poller.POLLIN)

        try {
            while (!Thread.currentThread().isInterrupted && !closed.get()) {
                poller.poll(-1)

                // Network → app queue
                if (poller.pollin(0)) {
                    val frames = networkSocket.receiveMultipart()
                    if (frames != null) {
                        receiveQueue.put(frames)
                    }
                }

                // Pipe signal → drain outgoing queue into the network
                if (poller.pollin(1)) {
                    for (frames in sendQueue.drainAll()) {
                        networkSocket.sendMultipart(frames)
                    }
                }
            }
        } catch (t: Throwable) {
            if (!closed.get()) logger.error("routerThread crashed", t)
        }
    }

    private fun assertNotCancelled() {
        if (closed.get()) throw InterruptedException()
    }

    companion object {
        const val MESSAGE_QUEUE_CAPACITY = 1000
    }
}
