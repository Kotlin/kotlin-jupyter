package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.catchAllIndependentlyAndMerge
import org.zeromq.ZMQ
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Thread-safe wrapper around a ZeroMQ socket that allows calling send/recv from different threads.
 *
 * Design:
 * - A single [routerThread] owns the real [networkSocket] (the one bound or connected to TCP/IPC/etc).
 * - Other threads never touch the network socket directly; they interact via:
 *   - A blocking queue [sendQueue] for outgoing messages
 *   - Callbacks for incoming messages are added via [onReceive]
 */
internal class ZmqSocketWithCancellationImpl(
    loggerFactory: KernelLoggerFactory,
    socketData: ZmqSocketData,
) : Closeable {
    private val logger = loggerFactory.getLogger(this::class, socketData.name)

    private val networkSocket = ZmqNetworkSocket(socketData)

    private val poller = socketData.zmqContext.poller(2)
    private val sendQueue = BlockingSignallingQueue<List<ByteArray>>(MESSAGE_QUEUE_CAPACITY)
    private val receiveCallbacks: MutableList<(List<ByteArray>) -> Unit> = CopyOnWriteArrayList()

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    // Router worker thread (the only thread that touches the ZMQ socket)
    private val routerThread: Thread =
        thread(
            start = false,
            name = "${socketData.name} router thread",
            block = ::processMessages,
        )

    @Synchronized
    internal fun bind(): Boolean {
        if (!networkSocket.bind()) return false
        start()
        logger.debug("listening on ${networkSocket.address}")
        return true
    }

    @Synchronized
    internal fun connect(): Boolean {
        if (!networkSocket.connect()) return false
        start()
        logger.debug("connected to ${networkSocket.address}")
        return true
    }

    internal fun sendMultipart(message: List<ByteArray>) {
        assertNotCancelled()
        sendQueue.put(message)
    }

    internal fun onReceive(callback: (List<ByteArray>) -> Unit) {
        receiveCallbacks.add(callback)
    }

    internal fun join() {
        assertNotCancelled()
        routerThread.join()
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        logger.debug("closing a socket")
        catchAllIndependentlyAndMerge(
            { networkSocket.close() },
            { sendQueue.close() },
            { poller.close() },
            { routerThread.interrupt() },
            { receiveCallbacks.clear() },
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
                        processMessage(frames)
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

    private fun processMessage(message: List<ByteArray>) {
        for (callback in receiveCallbacks) {
            callback(message)
        }
    }

    private fun assertNotCancelled() {
        if (closed.get()) throw InterruptedException()
    }

    companion object {
        const val MESSAGE_QUEUE_CAPACITY = 1000
    }
}
