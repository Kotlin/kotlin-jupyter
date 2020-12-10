package org.jetbrains.kotlin.jupyter.common

private val magicRegex = Regex("_(.)")

fun getNameForUser(name: String): String {
    return magicRegex.replace(name.toLowerCase()) { match ->
        match.groupValues[1].toUpperCase()
    }
}
