package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.slf4j.Logger
import java.io.File
import java.io.StringReader
import javax.xml.bind.JAXBElement

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

fun File.readIniConfig() =
        existsOrNull()?.let {
            catchAll { it.readLines().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap() }
        }

fun readJson(path: String) =
        Parser.default().parse(path) as JsonObject

fun JSONObject.toJsonObject() = Parser.default().parse(StringReader(toString())) as JsonObject