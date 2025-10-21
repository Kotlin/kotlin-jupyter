package org.jetbrains.kotlinx.jupyter.dependencies

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode

/**
 * Resolves dependency artifacts while keeping track of the source code locations
 * that initiated each request (to produce precise diagnostics).
 */
interface SourceAwareDependenciesResolver {
    /**
     * Returns true if this resolver understands the given [repository] descriptor.
     */
    fun acceptsRepository(repository: Repository): Boolean

    /**
     * Registers a [repository] to be used by this resolver.
     *
     * @param repository Repository descriptor.
     * @param sourceCodeLocation Optional source location for diagnostics.
     * @return Success containing whether the repository list was changed; or failure with diagnostics.
     */
    fun addRepository(
        repository: Repository,
        sourceCodeLocation: SourceCode.LocationWithId? = null,
    ): ResultWithDiagnostics<Boolean>

    /**
     * Returns true if this resolver can attempt to resolve the given coordinates string.
     */
    fun acceptsArtifact(artifactCoordinates: String): Boolean

    /**
     * Resolves the given [artifactRequests].
     *
     * @param artifactRequests List of artifact requests with optional locations for diagnostics.
     * @param resolveSources Whether to also try to fetch sources.
     * @return Resolved binaries (and optionally sources) or detailed diagnostics on failure.
     */
    suspend fun resolve(
        artifactRequests: List<ArtifactRequest>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts>
}
