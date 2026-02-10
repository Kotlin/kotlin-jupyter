package org.jetbrains.kotlinx.jupyter.util

fun String.toUpperCaseAsciiOnly(): String {
    val builder = StringBuilder(length)
    for (c in this) {
        builder.append(if (c in 'a'..'z') c.uppercaseChar() else c)
    }
    return builder.toString()
}

fun List<String>.trimEmptyLines(): List<String> {
    val first = indexOfFirst { it.isNotEmpty() }
    val last = indexOfLast { it.isNotEmpty() }
    return if (first == -1 || last == -1) emptyList() else subList(first, last + 1)
}
