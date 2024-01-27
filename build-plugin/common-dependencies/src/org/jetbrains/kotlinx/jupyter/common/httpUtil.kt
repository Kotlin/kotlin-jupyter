package org.jetbrains.kotlinx.jupyter.common

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.*

class ResponseWrapper(
    response: Response,
    val url: String,
) : Response by response

fun httpRequest(request: Request): ResponseWrapper {
    return ResponseWrapper(
        SimpleHttpClient.makeRequest(request),
        request.url,
    )
}

fun getHttp(url: String) = httpRequest(Request("GET", url))

fun getHttpWithAuth(url: String, username: String, token: String): ResponseWrapper {
    val request = Request("GET", url).withBasicAuth(username, token)
    return httpRequest(request)
}

fun Request.withBasicAuth(username: String, password: String): Request {
    val b64 = Base64.getEncoder().encode("$username:$password".toByteArray()).toString(Charsets.UTF_8)
    return this.header("Authorization", "Basic $b64")
}

fun Request.withJson(json: JsonElement): Request {
    return this
        .body(Json.encodeToString(json))
        .header("Content-Type", "application/json")
}

fun ResponseWrapper.assertSuccessful() {
    if (!status.successful) {
        throw IOException("Http request failed. Url = $url. Response = $text")
    }
}

inline fun <reified T> ResponseWrapper.decodeJson(): T {
    assertSuccessful()
    return Json.decodeFromString(text)
}

val ResponseWrapper.json: JsonElement get() = decodeJson()
val ResponseWrapper.jsonObject: JsonObject get() = decodeJson()
val ResponseWrapper.jsonArray: JsonArray get() = decodeJson()

inline fun <reified T> ResponseWrapper.decodeJsonIfSuccessfulOrNull(): T? {
    return if (!status.successful) null
    else Json.decodeFromString(text)
}

val ResponseWrapper.jsonOrNull: JsonElement? get() = decodeJsonIfSuccessfulOrNull()
val ResponseWrapper.jsonObjectOrNull: JsonObject? get() = decodeJsonIfSuccessfulOrNull()
val ResponseWrapper.jsonArrayOrNull: JsonArray? get() = decodeJsonIfSuccessfulOrNull()
