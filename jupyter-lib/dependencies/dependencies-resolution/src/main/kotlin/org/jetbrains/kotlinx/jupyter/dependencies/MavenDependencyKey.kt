package org.jetbrains.kotlinx.jupyter.dependencies

/**
 * Key that identifies a Maven dependency without its version.
 */
data class MavenDependencyKey(
    val groupId: String,
    val artifactId: String,
)
