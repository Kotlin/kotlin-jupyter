package org.jetbrains.kotlinx.jupyter.common

interface HttpClient {
    fun makeRequest(request: Request): Response
}
