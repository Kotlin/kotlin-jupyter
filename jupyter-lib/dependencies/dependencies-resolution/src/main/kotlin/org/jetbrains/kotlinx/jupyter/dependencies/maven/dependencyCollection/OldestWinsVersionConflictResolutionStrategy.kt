package org.jetbrains.kotlinx.jupyter.dependencies.maven.dependencyCollection

/**
 * Allows adding a version only when no versions are present yet for a given dependency
 * (i.e., first resolved version "wins").
 */
object OldestWinsVersionConflictResolutionStrategy : VersionConflictResolutionStrategy {
    override fun shouldAddVersion(
        key: MavenDependencyKey,
        currentVersions: Set<String>,
        newVersion: String,
    ): Boolean = currentVersions.isEmpty()
}
