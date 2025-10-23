package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import java.io.File
import java.util.EnumMap

private typealias VersionMap = MutableMap<String, File>
private typealias DependencyMap = MutableMap<DependencyKey, VersionMap>

private enum class DependencyType {
    BINARY,
    SOURCE,
}

/**
 * Persistent storage for resolved dependencies.
 * Allows specifying a resolution strategy for version conflicts and
 * retrieval of resolved artifacts.
 */
class DependencyCollector(
    private val versionConflictResolutionStrategy: VersionConflictResolutionStrategy,
) {
    private val dependencies = EnumMap<DependencyType, DependencyMap>(DependencyType::class.java)
    private val dependencySnapshots = EnumMap<DependencyType, MutableList<File>>(DependencyType::class.java)

    fun getSnapshot(): ResolvedArtifacts {
        val binaries = dependencySnapshots[DependencyType.BINARY]?.toList().orEmpty()
        val sources = dependencySnapshots[DependencyType.SOURCE]?.toList().orEmpty()
        dependencySnapshots.clear()
        return ResolvedArtifacts(binaries, sources)
    }

    fun addBinary(
        coordinates: MavenCoordinates,
        file: File,
    ) = appendVersionIfNeeded(DependencyType.BINARY, coordinates, file)

    fun addSource(
        coordinates: MavenCoordinates,
        file: File,
    ) = appendVersionIfNeeded(DependencyType.SOURCE, coordinates, file)

    private fun appendVersionIfNeeded(
        type: DependencyType,
        coordinates: MavenCoordinates,
        file: File,
    ): Boolean {
        val dependencyMap = dependencies.getOrPut(type) { mutableMapOf() }
        val key = DependencyKey(coordinates.groupId, coordinates.artifactId)
        val versionsMap = dependencyMap.getOrPut(key) { mutableMapOf() }
        val currentVersions = versionsMap.keys
        val newVersion = coordinates.version.orEmpty()

        return if (versionConflictResolutionStrategy.shouldAddVersion(key, currentVersions, newVersion)) {
            versionsMap[newVersion] = file
            dependencySnapshots.getOrPut(type) { mutableListOf() }.add(file)
            true
        } else {
            false
        }
    }
}
