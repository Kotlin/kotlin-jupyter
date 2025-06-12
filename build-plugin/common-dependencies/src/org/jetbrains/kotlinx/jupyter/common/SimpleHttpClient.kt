package org.jetbrains.kotlinx.jupyter.common

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object SimpleHttpClient : HttpClient {
    override fun makeRequest(request: Request): Response {
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
            val newRequest =
                buildRequest(request) {
                    url(newUrl)
                }

            return makeRequest(newRequest) // Recursive call for redirect
        }

        val stream: InputStream? =
            if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        return ResponseImpl(Status(responseCode), responseText)
    }
}

data class ResponseImpl(
    override val status: Status,
    override val text: String,
) : Response
