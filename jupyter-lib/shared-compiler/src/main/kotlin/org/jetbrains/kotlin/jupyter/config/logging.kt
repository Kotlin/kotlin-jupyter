package org.jetbrains.kotlin.jupyter.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun getLogger(name: String = "ikotlin"): Logger = LoggerFactory.getLogger(name)
fun <T> Logger.catchAll(msg: String = "", body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    this.error(msg, e)
    null
}
