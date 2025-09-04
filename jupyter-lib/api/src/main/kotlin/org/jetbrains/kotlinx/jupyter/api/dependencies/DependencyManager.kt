package org.jetbrains.kotlinx.jupyter.api.dependencies

import java.io.File

/**
 * Manages dependencies for the notebook.
 * Provides an interface for adding and handling new dependencies, enabling dynamic updates
 * to the binary and source classpath.
 */
interface DependencyManager {
    val resolver: DependencyResolver

    val currentBinaryClasspath: List<File>
    val currentSourcesClasspath: List<File>

    fun addBinaryClasspath(newClasspath: Collection<File>)

    fun addSourceClasspath(newClasspath: Collection<File>)
}
