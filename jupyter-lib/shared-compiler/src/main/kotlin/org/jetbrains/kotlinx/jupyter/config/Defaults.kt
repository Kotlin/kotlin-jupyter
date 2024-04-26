package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.libraries.KernelRepository
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates

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

val defaultRepositories =
    listOf(
        "https://repo.maven.apache.org/maven2/",
        "https://jitpack.io/",
    ).map(::KernelRepository)
val defaultRepositoriesCoordinates = defaultRepositories.map { MavenRepositoryCoordinates(it.path) }
