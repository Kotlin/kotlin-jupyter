package org.jetbrains.kotlinx.jupyter.util

fun String.escapeForIframe(): String {
    val text = this
    return buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\\' -> append("&bsol;")
                '/' -> append("&sol;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }
}
