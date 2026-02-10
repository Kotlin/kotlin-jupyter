package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.exceptions.renderException
import org.jetbrains.kotlinx.jupyter.streams.KernelStreams
import org.slf4j.Logger
import java.io.PrintStream

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
