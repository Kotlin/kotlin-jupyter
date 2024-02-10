package org.jetbrains.kotlinx.jupyter.common

interface Request {
    val method: String
    val url: String
    val headers: Map<String, String>
    val body: String?
}
