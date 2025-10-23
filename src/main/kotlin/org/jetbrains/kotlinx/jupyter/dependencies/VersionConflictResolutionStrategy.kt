package org.jetbrains.kotlinx.jupyter.dependencies

data class DependencyKey(
    val groupId: String,
    val artifactId: String,
)

/**
 * Interface representing a strategy for determining whether a file for a new version
 * of a dependency should be added to the current <version -> file> mapping
 */
interface VersionConflictResolutionStrategy {
    fun shouldAddVersion(
        key: DependencyKey,
        currentVersions: Set<String>,
        newVersion: String,
    ): Boolean
}

/**
 * This strategy allows adding a new version only if there are no existing versions
 * for the specified `DependencyKey` that were already resolved
 */
object OldestWinsVersionConflictResolutionStrategy : VersionConflictResolutionStrategy {
    override fun shouldAddVersion(
        key: DependencyKey,
        currentVersions: Set<String>,
        newVersion: String,
    ): Boolean = currentVersions.isEmpty()
}
