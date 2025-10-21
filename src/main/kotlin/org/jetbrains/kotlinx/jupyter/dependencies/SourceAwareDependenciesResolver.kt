package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.dependencies.ArtifactWithLocation

interface SourceAwareDependenciesResolver {
    fun acceptsRepository(repository: RepositoryDescription): Boolean

    fun addRepository(
        repository: RepositoryDescription,
        sourceCodeLocation: SourceCode.LocationWithId? = null,
    ): ResultWithDiagnostics<Boolean>

    fun acceptsArtifact(artifactCoordinates: String): Boolean

    suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts>
}
