package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.exceptions.catchAllIndependentlyAndMerge
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.Pipe
import java.nio.channels.SelectableChannel
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * A small utility that encapsulates an outgoing [BlockingQueue] coupled with a non-blocking [Pipe]
 * used to signal a poller thread that new items are available.
 *
 * - [put] puts an item into the queue and signals it to [signaller]
 * - [signaller] is the [Pipe.SourceChannel] that a poller should register for POLLIN to listen for
 *   signals about new items
 * - [drainAll] should be invoked by the poller when the pipe becomes readable; it drains the
 *   pipe and returns all pending items
 */
internal class BlockingSignallingQueue<T : Any>(
    capacity: Int,
) : Closeable {
    private val queue: BlockingQueue<T> = ArrayBlockingQueue(capacity)

    private val pipeSignaller = PipeSignaller()
    val signaller: SelectableChannel get() = pipeSignaller.signaller

    fun put(item: T) {
        queue.put(item)
        pipeSignaller.signal()
    }

    /**
     * Called by the poller thread when [signaller] is readable.
     * It returns all added items and removes them from the queue.
     */
    fun drainAll(): Sequence<T> {
        pipeSignaller.drain()
        return generateSequence(queue::poll)
    }

    override fun close() {
        catchAllIndependentlyAndMerge(
            pipeSignaller::close,
            queue::clear,
        )
    }
}

private class PipeSignaller : Closeable {
    private val sink: Pipe.SinkChannel
    val signaller: Pipe.SourceChannel

    private val signalMessage = ByteBuffer.allocate(1)
    private val drainBuffer = ByteBuffer.allocate(64)

    init {
        val pipe: Pipe = Pipe.open()
        sink = pipe.sink().apply { configureBlocking(false) }
        signaller = pipe.source().apply { configureBlocking(false) }
    }

    /**
     * Wakes up a [signaller], unblocking [java.nio.channels.Selector]s waiting for it
     */
    fun signal() {
        // best-effort non-blocking poke: if the pipe buffer is full, write may return 0 — it's fine
        try {
            signalMessage.clear()
            sink.write(signalMessage)
        } catch (_: ClosedChannelException) {
            // being closed: ignore
        }
    }

    /**
     * Consumes all pending signals. Should be called after unblocking the poller
     */
    fun drain() {
        while (true) {
            drainBuffer.clear()
            val n =
                try {
                    signaller.read(drainBuffer)
                } catch (_: Throwable) {
                    // if closed or any error — stop draining
                    break
                }
            if (n <= 0) break
        }
    }

    override fun close() {
        catchAllIndependentlyAndMerge(
            signaller::close,
            sink::close,
        )
    }
}
