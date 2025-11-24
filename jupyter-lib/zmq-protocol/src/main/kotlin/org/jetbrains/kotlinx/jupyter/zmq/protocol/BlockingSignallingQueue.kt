package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.exceptions.catchAllIndependentlyAndMerge
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.Pipe
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

    private val sink: Pipe.SinkChannel
    val signaller: Pipe.SourceChannel

    init {
        val pipe: Pipe = Pipe.open()
        sink = pipe.sink().apply { configureBlocking(false) }
        signaller = pipe.source().apply { configureBlocking(false) }
    }

    fun put(item: T) {
        queue.put(item)
        // best-effort non-blocking poke: if the pipe buffer is full, write may return 0 — it's fine
        try {
            sink.write(ByteBuffer.allocate(1))
        } catch (_: ClosedChannelException) {
            // being closed: ignore
        }
    }

    /**
     * Called by the poller thread when [signaller] is readable.
     * It returns all added items and removes them from the queue.
     */
    fun drainAll(): Sequence<T> {
        // Drain the pipe (consume all pending signals)
        val drainBuf = ByteBuffer.allocate(64)
        while (true) {
            drainBuf.clear()
            val n =
                try {
                    signaller.read(drainBuf)
                } catch (_: Throwable) {
                    // if closed or any error — stop draining
                    break
                }
            if (n <= 0) break
        }

        // Drain the outgoing queue and deliver everything
        return generateSequence(queue::poll)
    }

    override fun close() {
        catchAllIndependentlyAndMerge(
            { signaller.close() },
            { sink.close() },
            { queue.clear() },
        )
    }
}
