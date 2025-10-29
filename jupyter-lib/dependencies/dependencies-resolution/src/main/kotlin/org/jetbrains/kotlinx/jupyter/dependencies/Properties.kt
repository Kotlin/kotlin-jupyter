package org.jetbrains.kotlinx.jupyter.dependencies

private val dependencyResolutionProperties by lazy {
    readResourceAsIniFile(
        "dependencies-resolution.properties",
        AmperMavenDependenciesResolver::class.java.classLoader,
    )
}

val amperVersion: String by dependencyResolutionProperties

internal fun String.parseIniConfig() =
    lineSequence()
        .map { it.split('=') }
        .filter { it.count() == 2 }
        .associate { it[0] to it[1] }

internal fun readResourceAsIniFile(
    fileName: String,
    classLoader: ClassLoader,
) = classLoader
    .getResource(fileName)
    ?.readText()
    ?.parseIniConfig()
    .orEmpty()
