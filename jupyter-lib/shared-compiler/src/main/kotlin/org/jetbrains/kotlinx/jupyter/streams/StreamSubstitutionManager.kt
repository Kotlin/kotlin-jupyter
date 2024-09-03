package org.jetbrains.kotlinx.jupyter.streams

import org.jetbrains.kotlinx.jupyter.api.StreamSubstitutionType
import org.jetbrains.kotlinx.jupyter.streams.StreamSubstitutionManager.StdErr
import org.jetbrains.kotlinx.jupyter.streams.StreamSubstitutionManager.StdIn
import org.jetbrains.kotlinx.jupyter.streams.StreamSubstitutionManager.StdOut
import java.io.Closeable
import java.io.InputStream
import java.io.PrintStream
import kotlin.reflect.KMutableProperty0

private var systemErrStream: PrintStream
    get() = System.err
    set(value) {
        System.setErr(value)
    }

private var systemOutStream: PrintStream
    get() = System.out
    set(value) {
        System.setOut(value)
    }

private var systemInStream: InputStream
    get() = System.`in`
    set(value) {
        System.setIn(value)
    }

/**
 * An abstract class designed to manage substituting of standard streams with some custom streams.
 *
 * @param StreamT The type of the stream, which must be a subtype of [Closeable].
 * @param systemStreamProp A mutable property representing the system stream.
 *                         I.e., for STDOUT, it's the pair of [System.out] and [System.setOut]
 * @param kernelStreamProp A mutable property representing the kernel stream, which can be null.
 *                         Kernel stream is a separate globally available property, but it might be
 *                         accessed only by the kernel facilities, not by the external code.
 * @param substitutionEngineType The type of stream substitution engine to use.
 *
 * Classes [StdOut], [StdErr], and [StdIn] extend this class for specific stream handling.
 */
abstract class StreamSubstitutionManager<StreamT : Closeable>(
    systemStreamProp: KMutableProperty0<StreamT>,
    kernelStreamProp: KMutableProperty0<StreamT>?,
    private val delegatingStreamFactory: (delegateFactory: () -> StreamT) -> StreamT,
    substitutionEngineType: StreamSubstitutionType,
) {
    private val localSystemStream =
        ThreadLocalStream(
            systemStreamProp,
            yieldStreamFallback = { streams -> streams.systemStream },
        )

    private val localKernelStream =
        ThreadLocalStream(
            kernelStreamProp,
            yieldStream = { streams -> streams.kernelStream },
            yieldStreamFallback = { streams -> streams.systemStream },
        )

    private val defaultStreams = getStreamsFromProperties()

    private val engine =
        run {
            val dataFlowComponents =
                DataFlowComponents(
                    defaultStreams,
                    ::substituteStreams,
                    ::finalizeStreams,
                )
            when (substitutionEngineType) {
                StreamSubstitutionType.BLOCKING -> BlockingSubstitutionEngine(dataFlowComponents)
                StreamSubstitutionType.NON_BLOCKING -> NonBlockingSubstitutionEngine(dataFlowComponents)
            }
        }

    private fun createStreams(
        createSystemStream: (initial: StreamT?) -> StreamT,
        createKernelStream: (initial: StreamT?) -> StreamT?,
    ): Streams<StreamT> {
        val myInitStreams: Streams<StreamT> = defaultStreams

        val systemStream = localSystemStream.create(myInitStreams, null, createSystemStream)!!
        val kernelStream = localKernelStream.create(myInitStreams, systemStream, createKernelStream)

        return Streams(systemStream, kernelStream)
    }

    private fun substituteStreams(newStreams: Streams<StreamT>): Streams<StreamT> {
        val originalStreams = getStreamsFromProperties()

        localSystemStream.substitute(newStreams)
        localKernelStream.substitute(newStreams)

        return originalStreams
    }

    private fun getStreamsFromProperties(): Streams<StreamT> {
        val systemStream = localSystemStream.get()!!
        val kernelStream = localKernelStream.get()
        return Streams(systemStream, kernelStream)
    }

    private fun finalizeStreams(
        newStreams: Streams<StreamT>,
        oldStreams: Streams<StreamT>,
    ) {
        localSystemStream.finalize(newStreams, oldStreams)
        localKernelStream.finalize(newStreams, oldStreams)
    }

    fun <T> withSubstitutedStreams(
        systemStreamFactory: (initial: StreamT?) -> StreamT,
        kernelStreamFactory: (initial: StreamT?) -> StreamT?,
        body: () -> T,
    ): T {
        return engine.withDataSubstitution(
            dataFactory = { _ ->
                createStreams(systemStreamFactory, kernelStreamFactory)
            },
            body = body,
        )
    }

    private data class Streams<StreamT : Closeable>(
        val systemStream: StreamT,
        val kernelStream: StreamT?,
    )

    private inner class ThreadLocalStream(
        private val streamProp: KMutableProperty0<StreamT>?,
        private val yieldStreamFallback: (Streams<StreamT>) -> StreamT,
        private val yieldStream: (Streams<StreamT>) -> StreamT? = yieldStreamFallback,
    ) {
        private val localStreamProp = ThreadLocal<StreamT>()
        private val delegatingStream =
            delegatingStreamFactory {
                localStreamProp.get()
                    ?: yieldStream(defaultStreams)
                    ?: yieldStreamFallback(defaultStreams)
            }

        fun get() = streamProp?.get()

        fun create(
            defaultStreams: Streams<StreamT>,
            fallbackStream: StreamT?,
            factory: (initial: StreamT?) -> StreamT?,
        ): StreamT? {
            return when (val value = factory(yieldStream(defaultStreams))) {
                null -> if (streamProp == null) null else fallbackStream
                else -> value
            }
        }

        fun substitute(newStreams: Streams<StreamT>) {
            val newStream = yieldStream(newStreams)
            if (newStream != null) {
                streamProp?.set(delegatingStream)
            }
            localStreamProp.set(newStream)
        }

        fun finalize(
            newStreams: Streams<StreamT>,
            oldStreams: Streams<StreamT>,
        ) {
            val oldStream = yieldStream(oldStreams)
            if (oldStream != null) {
                streamProp?.set(oldStream)
            }

            localStreamProp.remove()

            yieldStream(newStreams)?.close()
        }
    }

    class StdOut(
        substitutionEngineType: StreamSubstitutionType,
    ) : StreamSubstitutionManager<PrintStream>(
            ::systemOutStream,
            KernelStreams::out,
            ::DelegatingPrintStream,
            substitutionEngineType,
        )

    class StdErr(
        substitutionEngineType: StreamSubstitutionType,
    ) : StreamSubstitutionManager<PrintStream>(
            ::systemErrStream,
            KernelStreams::err,
            ::DelegatingPrintStream,
            substitutionEngineType,
        )

    class StdIn(
        substitutionEngineType: StreamSubstitutionType,
    ) : StreamSubstitutionManager<InputStream>(
            ::systemInStream,
            null,
            ::DelegatingInputStream,
            substitutionEngineType,
        )
}
