package org.jetbrains.kotlinx.jupyter.dependencies

/**
 * Strategy for deciding whether a file for a new version of a dependency should be
 * added to the current mapping from version to file.
 */
interface VersionConflictResolutionStrategy {
    /**
     * @param key Dependency identity (group and artifact).
     * @param currentVersions Set of versions already accepted for this dependency.
     * @param newVersion Version being considered.
     * @return true if the new version should be added; false otherwise.
     */
    fun shouldAddVersion(
        key: MavenDependencyKey,
        currentVersions: Set<String>,
        newVersion: String,
    ): Boolean
}
