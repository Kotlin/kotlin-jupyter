package org.jetbrains.kotlin.jupyter.common

private val enumNameRegex = Regex("_(.)")

fun getNameForUser(name: String): String {
    return enumNameRegex.replace(name.toLowerCase()) { match ->
        match.groupValues[1].toUpperCase()
    }
}
