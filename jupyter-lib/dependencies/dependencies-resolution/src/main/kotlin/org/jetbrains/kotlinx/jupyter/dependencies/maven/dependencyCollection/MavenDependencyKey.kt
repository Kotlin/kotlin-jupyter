package org.jetbrains.kotlinx.jupyter.dependencies.maven.dependencyCollection

/**
 * Key that identifies a Maven dependency without its version.
 */
data class MavenDependencyKey(
    val groupId: String,
    val artifactId: String,
)
