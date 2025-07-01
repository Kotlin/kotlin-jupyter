package org.jetbrains.kotlinx.jupyter.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.Base64

class ResponseWrapper(
    response: Response,
    val url: String,
) : Response by response

fun HttpClient.httpRequest(request: Request): ResponseWrapper =
    ResponseWrapper(
        makeRequest(request),
        request.url,
    )

fun HttpClient.getHttp(url: String) = httpRequest(buildRequest("GET", url))

fun HttpClient.getHttpWithAuth(
    url: String,
    username: String,
    token: String,
): ResponseWrapper {
    val request =
        buildRequest("GET", url) {
            withBasicAuth(username, token)
        }
    return httpRequest(request)
}

fun RequestBuilder.withBasicAuth(
    username: String,
    password: String,
): RequestBuilder {
    val b64 = Base64.getEncoder().encode("$username:$password".toByteArray()).toString(Charsets.UTF_8)
    return header("Authorization", "Basic $b64")
}

fun RequestBuilder.withJson(json: JsonElement): RequestBuilder =
    this
        .body(Json.encodeToString(json))
        .header("Content-Type", "application/json")

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

inline fun <reified T> ResponseWrapper.decodeJsonIfSuccessfulOrNull(): T? =
    if (!status.successful) {
        null
    } else {
        Json.decodeFromString(text)
    }

val ResponseWrapper.jsonOrNull: JsonElement? get() = decodeJsonIfSuccessfulOrNull()
val ResponseWrapper.jsonObjectOrNull: JsonObject? get() = decodeJsonIfSuccessfulOrNull()
val ResponseWrapper.jsonArrayOrNull: JsonArray? get() = decodeJsonIfSuccessfulOrNull()
