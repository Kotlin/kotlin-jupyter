package org.jetbrains.kotlinx.jupyter.dependencies

import java.io.File

/**
 * Result of resolving a set of artifacts.
 *
 * @property binaries Files to put on the classpath (JARs or directories).
 * @property sources Optional source JARs/directories matching [binaries] for IDE features.
 */
data class ResolvedArtifacts(
    val binaries: List<File> = emptyList(),
    val sources: List<File> = emptyList(),
)
