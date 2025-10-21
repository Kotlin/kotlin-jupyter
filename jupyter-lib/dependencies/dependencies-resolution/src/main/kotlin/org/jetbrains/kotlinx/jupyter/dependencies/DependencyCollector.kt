package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import java.io.File
import java.util.EnumMap
import kotlin.collections.getOrPut

private typealias VersionMap = MutableMap<String, File>
private typealias DependencyMap = MutableMap<MavenDependencyKey, VersionMap>

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
    /**
     * Strategy used to decide how to handle multiple versions of the same dependency.
     */
    private val versionConflictResolutionStrategy: VersionConflictResolutionStrategy,
) {
    private val dependencies = EnumMap<DependencyType, DependencyMap>(DependencyType::class.java)
    private val dependencySnapshots = EnumMap<DependencyType, MutableList<File>>(DependencyType::class.java)

    /**
     * Returns and clears the list of artifacts added since the previous snapshot.
     */
    fun getSnapshot(): ResolvedArtifacts {
        val binaries = dependencySnapshots[DependencyType.BINARY]?.toList().orEmpty()
        val sources = dependencySnapshots[DependencyType.SOURCE]?.toList().orEmpty()
        dependencySnapshots.clear()
        return ResolvedArtifacts(binaries, sources)
    }

    /**
     * Records a resolved binary [file] for the given Maven [coordinates].
     */
    fun addBinary(
        coordinates: MavenCoordinates,
        file: File,
    ) = appendVersionIfNeeded(DependencyType.BINARY, coordinates, file)

    /**
     * Records a resolved source [file] for the given Maven [coordinates].
     */
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
        val key = MavenDependencyKey(coordinates.groupId, coordinates.artifactId)
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
