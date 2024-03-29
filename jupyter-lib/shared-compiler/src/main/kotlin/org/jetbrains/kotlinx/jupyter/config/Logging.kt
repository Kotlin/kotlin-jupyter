package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.exceptions.renderException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KMutableProperty0

fun getLogger(name: String = "ikotlin"): Logger = LoggerFactory.getLogger(name)

inline fun <reified T> logger(): Logger = LoggerFactory.getLogger(T::class.java)

object KernelStreams {
    private val outLock = ReentrantLock()
    private val errLock = ReentrantLock()

    private var _out: PrintStream = System.out
    private var _err: PrintStream = System.err

    val out: PrintStream get() = _out
    val err: PrintStream get() = _err

    private fun <T> withStream(
        streamProp: KMutableProperty0<PrintStream>,
        newStream: PrintStream,
        lock: ReentrantLock,
        body: () -> T,
    ): T {
        return lock.withLock {
            val originalStream = streamProp.get()
            try {
                streamProp.set(newStream)
                body()
            } finally {
                streamProp.set(originalStream)
            }
        }
    }

    fun <T> withOutStream(
        outStream: PrintStream,
        body: () -> T,
    ): T {
        return withStream(::_out, outStream, outLock, body)
    }

    fun <T> withErrStream(
        errStream: PrintStream,
        body: () -> T,
    ): T {
        return withStream(::_err, errStream, errLock, body)
    }
}

fun Logger.errorForUser(
    stream: PrintStream = KernelStreams.err,
    message: String,
    throwable: Throwable? = null,
) {
    if (throwable == null) {
        error(message)
    } else {
        error(message, throwable)
    }

    if (message.isNotEmpty()) {
        stream.print("[ERROR] ")
        stream.println(message)
    }
    throwable?.let { stream.println(it.renderException()) }
    stream.flush()
}

fun <T> Logger.catchAll(
    stream: PrintStream = KernelStreams.err,
    msg: String = "",
    body: () -> T,
): T? =
    try {
        body()
    } catch (e: Throwable) {
        this.errorForUser(stream, msg, e)
        null
    }
