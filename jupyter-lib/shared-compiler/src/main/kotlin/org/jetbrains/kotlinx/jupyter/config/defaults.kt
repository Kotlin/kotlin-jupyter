package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.libraries.KernelRepository

val defaultGlobalImports = listOf(
    "kotlin.math.*",
    "jupyter.kotlin.*",
    "org.jetbrains.kotlinx.jupyter.api.*",
    "org.jetbrains.kotlinx.jupyter.api.libraries.*",
)

val defaultRepositories = listOf(
    "https://repo.maven.apache.org/maven2/",
    "https://jitpack.io/",
).map(::KernelRepository)
