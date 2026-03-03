package org.jetbrains.kotlinx.jupyter.streams

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.concurrent.timer

/**
 * An [OutputStream] that captures the written content while optionally forwarding it
 * to a [parentStream] [PrintStream].
 * Capturing only happens if [captureOutput] is true.
 * The captured content is passed to a callback function [onCaptured] when certain buffer conditions are met.
 * These conditions are defined by [conf].
 *
 * @property parentStream The parent PrintStream to forward the output to.
 * @property conf Configuration for the capturing and flushing behavior.
 * @property captureOutput A flag indicating whether to capture output.
 * @property onCaptured A callback function called with the captured content.
 */
class CapturingOutputStream(
    private val parentStream: PrintStream?,
    private val conf: OutputConfig,
    private val captureOutput: Boolean,
    val onCaptured: (String) -> Unit,
) : OutputStream() {
    private val capturedLines = ByteArrayOutputStream()
    private val capturedNewLine = ByteArrayOutputStream()
    private var overallOutputSize = 0
    private var newlineFound = false

    private val timer =
        timer(
            initialDelay = conf.captureBufferTimeLimitMs,
            period = conf.captureBufferTimeLimitMs,
            action = {
                flush()
            },
        )

    val contents: ByteArray
        @TestOnly
        get() = capturedLines.toByteArray() + capturedNewLine.toByteArray()

    private fun flushIfNeeded(b: Int) {
        val c = b.toChar()
        if (c == '\n') {
            newlineFound = true
            capturedNewLine.writeTo(capturedLines)
            capturedNewLine.reset()
        }

        val size = capturedLines.size() + capturedNewLine.size()

        if (newlineFound && size >= conf.captureNewlineBufferSize) {
            return flushBuffers(capturedLines)
        }
        if (size >= conf.captureBufferMaxSize) {
            return flush()
        }
    }

    @Synchronized
    override fun write(b: Int) {
        ++overallOutputSize
        parentStream?.write(b)

        if (captureOutput && overallOutputSize <= conf.cellOutputMaxSize) {
            capturedNewLine.write(b)
            flushIfNeeded(b)
        }
    }

    @Synchronized
    private fun flushBuffers(vararg buffers: ByteArrayOutputStream) {
        newlineFound = false
        val str =
            buffers
                .map { stream ->
                    val str = stream.toString("UTF-8")
                    stream.reset()
                    str
                }.reduce { acc, s -> acc + s }
        if (str.isNotEmpty()) {
            onCaptured(str)
        }
    }

    override fun flush() {
        flushBuffers(capturedLines, capturedNewLine)
    }

    override fun close() {
        flush()
        super.close()
        timer.cancel()
    }
}
