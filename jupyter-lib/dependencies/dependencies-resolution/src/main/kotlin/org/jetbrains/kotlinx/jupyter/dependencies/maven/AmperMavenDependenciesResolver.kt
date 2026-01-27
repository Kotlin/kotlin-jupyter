package org.jetbrains.kotlinx.jupyter.dependencies.maven

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.kotlinx.jupyter.dependencies.api.ArtifactRequest
import org.jetbrains.kotlinx.jupyter.dependencies.api.MAVEN_LOCAL_NAME
import org.jetbrains.kotlinx.jupyter.dependencies.api.Repository
import org.jetbrains.kotlinx.jupyter.dependencies.api.ResolvedArtifacts
import org.jetbrains.kotlinx.jupyter.dependencies.api.SourceAwareDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.maven.artifacts.toMavenArtifact
import org.jetbrains.kotlinx.jupyter.dependencies.maven.dependencyCollection.MavenDependencyCollector
import org.jetbrains.kotlinx.jupyter.dependencies.maven.dependencyCollection.OldestWinsVersionConflictResolutionStrategy
import org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories.amperRepositoryComparator
import org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories.convertToAmperRepository
import org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories.toRepositoryUrlOrNull
import org.jetbrains.kotlinx.jupyter.dependencies.maven.resolution.doAmperResolve
import org.jetbrains.kotlinx.jupyter.dependencies.util.dependencyResolutionProperties
import java.nio.file.Path
import java.util.TreeSet
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess

private val amperVersion: String by dependencyResolutionProperties

/**
 * Resolves Maven coordinates using Amper's dependency resolution engine.
 *
 * Note: this type is used at runtime, but is relocated into a fat JAR, so the IDE may mark it as unused.
 */
@Suppress("unused")
class AmperMavenDependenciesResolver(
    cachePath: Path,
) : SourceAwareDependenciesResolver {
    // Deprioritize Central repo
    private val mavenCachePath = cachePath.resolve(".m2")
    private val incrementalCachePath = cachePath.resolve(".incrementalCache")

    private val repos = TreeSet(amperRepositoryComparator)

    private val requestedArtifacts = mutableMapOf<String, ArtifactRequest>()
    private val dependencyCollector = MavenDependencyCollector(OldestWinsVersionConflictResolutionStrategy)

    var forceCacheRefresh: Boolean = false

    /**
     * Returns true if the repository URL looks valid for Maven resolution.
     */
    override fun acceptsRepository(repository: Repository): Boolean =
        repository.value == MAVEN_LOCAL_NAME || repository.value.toRepositoryUrlOrNull() != null

    /**
     * Adds a Maven repository to be used during resolution. Username/password may be taken from
     * environment variables if specified with the special syntax, see `tryResolveEnvironmentVariable`.
     */
    override fun addRepository(
        repository: Repository,
        sourceCodeLocation: SourceCode.LocationWithId?,
    ): ResultWithDiagnostics<Boolean> {
        val repo =
            when (val result = repository.convertToAmperRepository(sourceCodeLocation)) {
                is ResultWithDiagnostics.Failure -> return result
                is ResultWithDiagnostics.Success -> result.value ?: return false.asSuccess()
            }

        return repos.add(repo).asSuccess()
    }

    /**
     * Returns true if [artifactCoordinates] parse as Maven coordinates.
     */
    override fun acceptsArtifact(artifactCoordinates: String): Boolean = artifactCoordinates.toMavenArtifact() != null

    /**
     * Resolves the given artifacts via Amper resolver and returns all newly added files from the collector snapshot.
     */
    override suspend fun resolve(
        artifactRequests: List<ArtifactRequest>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts> {
        for (artifact in artifactRequests) {
            requestedArtifacts[artifact.artifact] = artifact
        }
        val result =
            doAmperResolve(
                resolutionContext = createResolutionContext(),
                currentArtifactsWithLocations = artifactRequests,
                allArtifactsWithLocations = requestedArtifacts.values,
                resolveSources = resolveSources,
                forceCacheRefresh = forceCacheRefresh,
                dependencyCollector = dependencyCollector,
            )
        return when (result) {
            is ResultWithDiagnostics.Failure -> {
                result
            }
            is ResultWithDiagnostics.Success -> {
                dependencyCollector.getSnapshot().asSuccess()
            }
        }
    }

    private fun createResolutionContext(): Context =
        Context {
            scope = ResolutionScope.RUNTIME
            platforms = setOf(ResolutionPlatform.JVM)
            repositories = repos.toList()
            cache = getDefaultFileCacheBuilder(mavenCachePath)

            // This option makes resolution faster,
            // but it also makes it less reliable - if the jar was changed
            // locally, it won't be re-resolved. We're OK with it for now
            verifyChecksumsLocally = false

            // Incremental cache is used to cache whole resolution trees
            // (not only resolved artifacts).
            // It also makes resolution faster.
            incrementalCache =
                IncrementalCache(
                    stateRoot = incrementalCachePath,
                    // When the Amper is updated, the incremental cache should be invalidated
                    codeVersion = amperVersion,
                )
        }
}
