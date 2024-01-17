package org.jetbrains.kotlinx.jupyter.util

fun String.toUpperCaseAsciiOnly(): String {
    val builder = StringBuilder(length)
    for (c in this) {
        builder.append(if (c in 'a'..'z') c.uppercaseChar() else c)
    }
    return builder.toString()
}
