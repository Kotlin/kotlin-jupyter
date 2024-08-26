package org.jetbrains.kotlinx.jupyter.util

import org.jetbrains.kotlinx.jupyter.api.StreamSubstitutionType
import org.jetbrains.kotlinx.jupyter.config.KernelStreams
import org.jetbrains.kotlinx.jupyter.util.StreamSubstitutionManager.StdErr
import org.jetbrains.kotlinx.jupyter.util.StreamSubstitutionManager.StdIn
import org.jetbrains.kotlinx.jupyter.util.StreamSubstitutionManager.StdOut
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
    private val systemStreamProp: KMutableProperty0<StreamT>,
    private val kernelStreamProp: KMutableProperty0<StreamT>?,
    substitutionEngineType: StreamSubstitutionType,
) {
    private val engine =
        run {
            val dataFlowComponents =
                DataFlowComponents(
                    Streams(systemStreamProp.get(), kernelStreamProp?.get()),
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
        initial: Streams<StreamT>?,
    ): Streams<StreamT> {
        val systemStream = createSystemStream(initial?.systemStream)
        val userStream = createKernelStream(initial?.kernelStream) ?: systemStream

        return Streams(systemStream, userStream)
    }

    private fun substituteStreams(newStreams: Streams<StreamT>): Streams<StreamT> {
        val originalSystemStream = systemStreamProp.get()
        val originalKernelStream = kernelStreamProp?.get()

        systemStreamProp.set(newStreams.systemStream)

        val newKernelStream = kernelStreamProp?.get()
        if (newKernelStream != null) {
            kernelStreamProp?.set(newKernelStream)
        }

        return Streams(originalSystemStream, originalKernelStream)
    }

    private fun finalizeStreams(
        newStreams: Streams<StreamT>,
        oldStreams: Streams<StreamT>,
    ) {
        val oldSystemStream = oldStreams.systemStream
        val oldKernelStream = oldStreams.kernelStream

        systemStreamProp.set(oldSystemStream)
        if (oldKernelStream != null) {
            kernelStreamProp?.set(oldKernelStream)
        }

        newStreams.systemStream.close()
        newStreams.kernelStream?.close()
    }

    fun <T> withSubstitutedStreams(
        systemStreamFactory: (initial: StreamT?) -> StreamT,
        kernelStreamFactory: (initial: StreamT?) -> StreamT?,
        body: () -> T,
    ): T {
        return engine.withDataSubstitution(
            dataFactory = { initialStreams ->
                createStreams(systemStreamFactory, kernelStreamFactory, initialStreams)
            },
            body = body,
        )
    }

    private class Streams<StreamT : Closeable>(
        val systemStream: StreamT,
        val kernelStream: StreamT?,
    )

    class StdOut(
        substitutionEngineType: StreamSubstitutionType,
    ) : StreamSubstitutionManager<PrintStream>(
            ::systemOutStream,
            KernelStreams::out,
            substitutionEngineType,
        )

    class StdErr(
        substitutionEngineType: StreamSubstitutionType,
    ) : StreamSubstitutionManager<PrintStream>(
            ::systemErrStream,
            KernelStreams::err,
            substitutionEngineType,
        )

    class StdIn(
        substitutionEngineType: StreamSubstitutionType,
    ) : StreamSubstitutionManager<InputStream>(
            ::systemInStream,
            null,
            substitutionEngineType,
        )
}
