package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.libraries.KernelRepository
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates

val defaultRepositories =
    listOf(
        "https://repo.maven.apache.org/maven2/",
    ).map(::KernelRepository)
val defaultRepositoriesCoordinates = defaultRepositories.map { MavenRepositoryCoordinates(it.path) }
