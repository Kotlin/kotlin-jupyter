package org.jetbrains.kotlinx.jupyter.common

class RequestBuilder {
    private var method: String = ""
    private var url: String = ""
    private val headers: MutableMap<String, String> = mutableMapOf()
    private var body: String? = null

    fun method(method: String) = apply { this.method = method }

    fun url(url: String) = apply { this.url = url }

    fun header(
        name: String,
        value: String,
    ) = apply { this.headers[name] = value }

    fun body(body: String?) = apply { this.body = body }

    fun build(): Request = RequestImpl(method, url, headers, body)
}

fun buildRequest(buildAction: RequestBuilder.() -> Unit): Request =
    RequestBuilder()
        .apply(buildAction)
        .build()

fun buildRequest(
    method: String,
    url: String,
    headers: Map<String, String> = mapOf(),
    body: String? = null,
    buildAction: RequestBuilder.() -> Unit = {},
): Request =
    buildRequest {
        method(method)
        url(url)
        for ((key, value) in headers) {
            header(key, value)
        }
        body(body)

        buildAction()
    }

fun buildRequest(
    request: Request,
    buildAction: RequestBuilder.() -> Unit,
): Request =
    buildRequest(
        request.method,
        request.url,
        request.headers,
        request.body,
        buildAction,
    )

private class RequestImpl(
    override val method: String,
    override val url: String,
    override val headers: Map<String, String>,
    override val body: String?,
) : Request
