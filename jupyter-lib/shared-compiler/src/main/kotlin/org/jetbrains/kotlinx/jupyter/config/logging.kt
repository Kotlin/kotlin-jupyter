package org.jetbrains.kotlinx.jupyter.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun getLogger(name: String = "ikotlin"): Logger = LoggerFactory.getLogger(name)

fun Logger.errorForUser(message: String, throwable: Throwable? = null) {
    if (throwable == null) error(message)
    else error(message, throwable)

    val stream = System.out
    val msgBuffer = StringBuilder().apply {
        append("[ERROR] ")
        append(message)
    }

    stream.println(msgBuffer)
    throwable?.printStackTrace(stream)
    stream.flush()
}

fun <T> Logger.catchAll(msg: String = "", body: () -> T): T? = try {
    body()
} catch (e: Throwable) {
    this.errorForUser(msg, e)
    null
}
