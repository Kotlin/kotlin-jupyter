package org.jetbrains.kotlinx.jupyter.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun getLogger(name: String = "ikotlin"): Logger = LoggerFactory.getLogger(name)
fun <T> Logger.catchAll(msg: String = "", body: () -> T): T? = try {
    body()
} catch (e: Throwable) {
    this.error(msg, e)
    null
}
