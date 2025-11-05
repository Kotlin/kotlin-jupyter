package org.jetbrains.kotlinx.jupyter.dependencies.util

import org.jetbrains.kotlinx.jupyter.dependencies.maven.AmperMavenDependenciesResolver

internal val dependencyResolutionProperties by lazy {
    readResourceAsIniFile(
        "dependencies-resolution.properties",
        AmperMavenDependenciesResolver::class.java.classLoader,
    )
}

private fun String.parseIniConfig() =
    lineSequence()
        .map { it.split('=') }
        .filter { it.count() == 2 }
        .associate { it[0].trim() to it[1].trim() }

private fun readResourceAsIniFile(
    fileName: String,
    classLoader: ClassLoader,
) = classLoader
    .getResource(fileName)
    ?.readText()
    ?.parseIniConfig()
    .orEmpty()
