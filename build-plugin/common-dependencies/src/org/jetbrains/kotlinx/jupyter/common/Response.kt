package org.jetbrains.kotlinx.jupyter.common

interface Response {
    val status: Status
    val text: String
}

data class Status(
    val code: Int,
)

val Status.successful: Boolean get() = code in 200..299
