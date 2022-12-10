package org.jetbrains.kotlinx.jupyter.common

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.http4k.asString
import org.http4k.client.ApacheClient
import org.http4k.client.PreCannedApacheHttpClients
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import java.io.IOException
import java.util.Base64

class ResponseWrapper(
    response: Response,
    val url: String,
) : Response by response

fun httpRequest(request: Request): ResponseWrapper {
    PreCannedApacheHttpClients.defaultApacheHttpClient().use { closeableHttpClient ->
        val apacheClient = ApacheClient(client = closeableHttpClient)
        val client = ClientFilters.FollowRedirects().then(apacheClient)
        val response = client(request)

        return ResponseWrapper(response, request.uri.toString())
    }
}

fun getHttp(url: String) = httpRequest(Request(Method.GET, url))

fun getHttpWithAuth(url: String, username: String, token: String): ResponseWrapper {
    val request = Request(Method.GET, url).withBasicAuth(username, token)
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

val Response.text: String get() {
    return body.payload.asString()
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
