package org.jetbrains.kotlinx.jupyter.common

private val enumNameRegex = Regex("_(.)")

@OptIn(ExperimentalStdlibApi::class)
fun getNameForUser(name: String): String =
    enumNameRegex.replace(name.lowercase()) { match ->
        match.groupValues[1].uppercase()
    }
