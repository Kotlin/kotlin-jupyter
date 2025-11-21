package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import zmq.ZError
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.Pipe
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe wrapper around a ZeroMQ socket that allows calling send/recv from different threads.
 *
 * Design:
 * - A single "router thread" owns the real network socket (the one bound or connected to TCP/IPC/etc).
 * - Other threads never touch the network socket directly; they interact via:
 *   - A blocking queue (app -> router thread) for outgoing messages plus a Pipe signal,
 *   - A blocking queue (router thread -> app) for incoming messages.
 *
 * Properties:
 * - Blocking queues provide backpressure and simple thread-safety.
 * - Both the network socket and the router loop can be cleanly interrupted on close().
 * - Poller watches the network socket and a SelectableChannel.
 */
class ZmqSocketWithCancellationImpl(
    loggerFactory: KernelLoggerFactory,
    socketData: ZmqSocketData,
) : ZmqSocketWithCancellation {
    private val logger = loggerFactory.getLogger(this::class)

    private val name: String = socketData.name
    private val address: String = socketData.address
    private val socketType: SocketType = socketData.socketType

    // The real network socket (owned only by routerThread)
    private val networkSocket: ZMQ.Socket =
        with(socketData) {
            zmqContext.socket(socketType).apply {
                linger = 0
                identity = socketIdentity
                if (socketType == SocketType.ROUTER) {
                    setRouterHandover(true)
                    setRouterMandatory(true)
                }
            }
        }
    private val networkCancel: ZMQ.CancellationToken = networkSocket.createCancellationToken()

    // Poller watches: [0] networkSocket (POLLIN), [1] pipeSource (POLLIN)
    private val poller = socketData.zmqContext.poller(2)

    // Pipe used to wake the poll loop when new outgoing messages are enqueued
    private val sendSignalPipe: Pipe =
        Pipe.open().apply {
            sink().configureBlocking(false)
            source().configureBlocking(false)
        }
    private val pipeSink = sendSignalPipe.sink()
    private val pipeSource = sendSignalPipe.source()

    // Queues connecting public API to the router thread and back
    private val sendQueue: BlockingQueue<List<ByteArray>> = ArrayBlockingQueue(10_000)
    private val receiveQueue: BlockingQueue<List<ByteArray>> = ArrayBlockingQueue(10_000)

    // Router worker thread (the only thread that touches the ZMQ socket)
    private lateinit var routerThread: Thread

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Synchronized
    internal fun bind(): Boolean {
        val ok = networkSocket.bind(address)
        if (socketType == SocketType.PUB) {
            // Classic slow-joiner workaround
            Thread.sleep(500)
        }
        start()
        logger.debug("[$name] listening on $address")
        return ok
    }

    @Synchronized
    internal fun connect(): Boolean {
        val ok = networkSocket.connect(address)
        start()
        logger.debug("[$name] connected to $address")
        return ok
    }

    override fun subscribe(topic: ByteArray): Boolean = networkSocket.subscribe(topic)

    override fun sendMultipart(message: Sequence<ByteArray>) {
        assertNotCancelled()
        // 1) Put the message into the outgoing queue
        sendQueue.put(message.toList())
        // 2) Non-blocking "poke" to wake the poll loop via the pipe
        //    If pipe buffer is full, write() may return 0 — that’s fine (signals coalesce).
        try {
            val buf = ByteBuffer.allocate(1)
            buf.put(0, 1)
            pipeSink.write(buf) // non-blocking; may write 0 bytes
        } catch (_: ClosedChannelException) {
            // being closed: ignore
        } catch (_: Throwable) {
            // best-effort signal; ignore unexpected issues to avoid breaking the caller
        }
    }

    override fun recvMultipart(): Sequence<ByteArray> {
        assertNotCancelled()
        val frames = receiveQueue.take() // blocking read from app-facing queue
        return frames.asSequence()
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        tryFinally(
            action = {
                // Cancel token to interrupt any blocking recv() on the network socket
                if (!networkCancel.isCancellationRequested) {
                    networkCancel.cancel()
                }
                // Close channels to wake poller
                try {
                    pipeSource.close()
                } catch (_: Throwable) {
                }
                try {
                    pipeSink.close()
                } catch (_: Throwable) {
                }
                routerThread.interrupt()
            },
            finally = {
                joinQuiet(routerThread)
                safeClose(networkSocket)
            },
        )
    }

    // ---------------- Private implementation ----------------

    /** Starts the router thread once (idempotent), after bind/connect. */
    @Synchronized
    private fun start() {
        if (!started.compareAndSet(false, true)) return
        assertNotCancelled()

        routerThread =
            Thread({
                // Register both the ZMQ socket and the SelectableChannel
                poller.register(networkSocket, ZMQ.Poller.POLLIN)
                poller.register(pipeSource, ZMQ.Poller.POLLIN)

                // Buffer to drain the pipe when it signals (size is arbitrary; we loop until empty)
                val drainBuf = ByteBuffer.allocate(64)

                try {
                    while (!Thread.currentThread().isInterrupted && !closed.get()) {
                        poller.poll(-1)

                        // Network → app queue
                        if (poller.pollin(0)) {
                            val frames = recvMultipartFrom(networkSocket, networkCancel)
                            if (frames != null) {
                                receiveQueue.put(frames)
                            }
                        }

                        // Pipe signal → drain sendQueue into the network
                        if (poller.pollin(1)) {
                            // Drain the pipe (consume all pending signals)
                            do {
                                drainBuf.clear()
                            } while (pipeSource.read(drainBuf) > 0)

                            // Drain the outgoing queue and send everything
                            while (true) {
                                val msg = sendQueue.poll() ?: break
                                sendMultipartTo(networkSocket, msg)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (!closed.get()) logger.error("[$name] routerThread crashed", t)
                } finally {
                    try {
                        pipeSource.close()
                    } catch (_: Throwable) {
                    }
                }
            }, "$name-router").also { it.start() }
    }

    /**
     * Receive a full multipart message from the given socket.
     * Returns null only when the call was interrupted via cancellation.
     */
    private fun recvMultipartFrom(
        socket: ZMQ.Socket,
        cancelToken: ZMQ.CancellationToken,
    ): List<ByteArray>? {
        val firstFrame: ByteArray =
            try {
                socket.recv(0, cancelToken) ?: return null
            } catch (e: ZMQException) {
                if (e.errorCode == ZMQ.Error.EINTR.code || e.errorCode == ZError.ECANCELED) return null
                throw e
            }

        val frames = ArrayList<ByteArray>(4)
        frames.add(firstFrame)
        while (socket.hasReceiveMore()) {
            frames.add(socket.recv())
        }
        return frames
    }

    /** Send the entire multipart message to the given socket (all-or-nothing semantics). */
    private fun sendMultipartTo(
        socket: ZMQ.Socket,
        frames: List<ByteArray>,
    ) {
        for (i in frames.indices) {
            val flags = if (i < frames.lastIndex) ZMQ.SNDMORE else 0
            socket.send(frames[i], flags)
        }
    }

    private fun assertNotCancelled() {
        if (closed.get()) throw InterruptedException()
    }

    private fun safeClose(s: ZMQ.Socket?) =
        try {
            s?.close()
        } catch (_: Throwable) {
        }

    private fun joinQuiet(t: Thread?) =
        try {
            t?.join(500)
        } catch (_: InterruptedException) {
        }
}
