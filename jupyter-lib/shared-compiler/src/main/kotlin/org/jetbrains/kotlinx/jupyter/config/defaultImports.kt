package org.jetbrains.kotlinx.jupyter.config

import kotlin.script.experimental.dependencies.RepositoryCoordinates

val MAVEN_CENTRAL = RepositoryCoordinates("https://repo.maven.apache.org/maven2/")
val JITPACK = RepositoryCoordinates("https://jitpack.io/")

val defaultRepositories = listOf(
    MAVEN_CENTRAL,
    JITPACK,
)

val defaultGlobalImports = listOf(
    "kotlin.math.*",
    "jupyter.kotlin.*",
    "org.jetbrains.kotlinx.jupyter.api.*",
    "org.jetbrains.kotlinx.jupyter.api.libraries.*",
)
