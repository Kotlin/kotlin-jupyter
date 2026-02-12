package org.jetbrains.kotlinx.jupyter.config

private val dateTimeImports =
    listOf(
        "nanoseconds",
        "microseconds",
        "milliseconds",
        "seconds",
        "minutes",
        "hours",
        "days",
    ).map { "kotlin.time.Duration.Companion.$it" }

val defaultGlobalImports =
    buildList {
        add("kotlin.math.*")
        add("jupyter.kotlin.*")
        add("org.jetbrains.kotlinx.jupyter.api.*")
        add("org.jetbrains.kotlinx.jupyter.api.libraries.*")
        add("org.jetbrains.kotlinx.jupyter.api.outputs.*")
        addAll(dateTimeImports)
    }
