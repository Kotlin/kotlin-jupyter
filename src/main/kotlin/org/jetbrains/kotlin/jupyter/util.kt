package org.jetbrains.kotlin.jupyter

import kotlinx.coroutines.*
import org.slf4j.Logger
import java.io.File

fun <T> catchAll(body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    null
}

fun <T> Logger.catchAll(msg: String = "", body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    this.error(msg, e)
    null
}

fun <T> T.validOrNull(predicate: (T) -> Boolean): T? = if (predicate(this)) this else null

fun <T> T.asDeferred(): Deferred<T> = this.let { GlobalScope.async { it } }

fun File.existsOrNull() = if (exists()) this else null

fun <T, R> Deferred<T>.asyncLet(selector: suspend (T) -> R): Deferred<R> = this.let {
    GlobalScope.async {
        selector(it.await())
    }
}

fun <T> Deferred<T>.awaitBlocking(): T = if (isCompleted) getCompleted() else runBlocking { await() }

fun String.parseIniConfig() =
        split("\n").map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun List<String>.joinToLines() = joinToString("\n")

fun File.tryReadIniConfig() =
        existsOrNull()?.let {
            catchAll { it.readText().parseIniConfig() }
        }
