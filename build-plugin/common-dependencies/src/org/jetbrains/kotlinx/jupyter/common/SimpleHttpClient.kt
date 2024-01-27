package org.jetbrains.kotlinx.jupyter.common

import java.net.HttpURLConnection
import java.net.URL

data class Request(
    val method: String,
    val url: String,
) {
    private val _headers = mutableMapOf<String, String>()
    val headers: Map<String, String> = _headers

    private var _body: String? = null
    val body: String? = _body

    fun header(key: String, value: String) = apply {
        _headers[key] = value
    }

    fun body(body: String) = apply {
        this._body = body
    }
}

object SimpleHttpClient {
    fun makeRequest(request: Request): Response {
        val url = URL(request.url)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = request.method
        request.headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        request.body?.let {
            connection.doOutput = true
            connection.outputStream.use { os ->
                os.write(it.toByteArray())
                os.flush()
            }
        }

        val responseCode = connection.responseCode

        // Check for redirect
        if (responseCode in 300..399) {
            val newUrl = connection.getHeaderField("Location")
            return makeRequest(request.copy(url = newUrl)) // Recursive call for redirect
        }

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream.bufferedReader().use { it.readText() }
        }

        return ResponseImpl(Status(responseCode), responseText)
    }
}

interface Response {
    val status: Status
    val text: String
}

data class ResponseImpl(override val status: Status, override val text: String) : Response

data class Status(val code: Int)

val Status.successful: Boolean get() = code == HttpURLConnection.HTTP_OK
