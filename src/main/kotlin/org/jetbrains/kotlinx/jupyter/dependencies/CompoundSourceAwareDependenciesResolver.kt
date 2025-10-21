package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.impl.makeResolveFailureResult

/**
 * A compound resolver that composes multiple SourceAwareDependenciesResolver implementations.
 * Behaves similarly to kotlin.script.experimental.dependencies.CompoundDependenciesResolver,
 * but works with ResolvedArtifacts and supports resolving sources.
 */
class CompoundSourceAwareDependenciesResolver(
    val resolvers: List<SourceAwareDependenciesResolver>,
) : SourceAwareDependenciesResolver {
    constructor(vararg resolvers: SourceAwareDependenciesResolver) : this(resolvers.toList())

    override fun acceptsArtifact(artifactCoordinates: String): Boolean = resolvers.any { it.acceptsArtifact(artifactCoordinates) }

    override fun acceptsRepository(repository: RepositoryDescription): Boolean = resolvers.any { it.acceptsRepository(repository) }

    override fun addRepository(
        repository: RepositoryDescription,
        sourceCodeLocation: SourceCode.LocationWithId?,
    ): ResultWithDiagnostics<Boolean> {
        var success = false
        var repositoryAdded = false
        val reports = mutableListOf<ScriptDiagnostic>()

        for (resolver in resolvers) {
            if (resolver.acceptsRepository(repository)) {
                when (val resolveResult = resolver.addRepository(repository, sourceCodeLocation)) {
                    is ResultWithDiagnostics.Success -> {
                        success = true
                        repositoryAdded = repositoryAdded || resolveResult.value
                        reports.addAll(resolveResult.reports)
                    }
                    is ResultWithDiagnostics.Failure -> reports.addAll(resolveResult.reports)
                }
            }
        }

        return when {
            success -> repositoryAdded.asSuccess(reports)
            reports.isEmpty() ->
                makeResolveFailureResult(
                    "No dependency resolver found that recognizes the repository coordinates '${'$'}repository'",
                    sourceCodeLocation,
                )
            else -> ResultWithDiagnostics.Failure(reports)
        }
    }

    override suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts> {
        val reports = mutableListOf<ScriptDiagnostic>()
        val binaries = mutableListOf<java.io.File>()
        val sources = mutableListOf<java.io.File>()
        var hadErrors = false

        // For each artifact, try resolvers in order. Aggregate diagnostics; stop at first success per artifact.
        for (artifactWithLocation in artifactsWithLocations) {
            val (artifact, sourceCodeLocation) = artifactWithLocation

            var resolved = false
            var hadAttempt = false

            for (resolver in resolvers) {
                if (!resolver.acceptsArtifact(artifact)) continue
                hadAttempt = true
                when (val res = resolver.resolve(listOf(artifactWithLocation), resolveSources)) {
                    is ResultWithDiagnostics.Success -> {
                        binaries.addAll(res.value.binaries)
                        sources.addAll(res.value.sources)
                        reports.addAll(res.reports)
                        resolved = true
                        break
                    }
                    is ResultWithDiagnostics.Failure -> {
                        reports.addAll(res.reports)
                        // try next resolver
                    }
                }
            }

            if (!resolved) {
                hadErrors = true
                if (!hadAttempt) {
                    // No resolver accepted this artifact at all
                    reports.add(
                        ScriptDiagnostic(
                            code = ScriptDiagnostic.unspecifiedError,
                            message = "No suitable dependency resolver found for artifact '${'$'}artifact'",
                            severity = ScriptDiagnostic.Severity.ERROR,
                            locationWithId = sourceCodeLocation,
                            exception = null,
                        ),
                    )
                }
            }
        }

        return if (hadErrors) {
            ResultWithDiagnostics.Failure(reports)
        } else {
            ResolvedArtifacts(
                binaries = binaries,
                sources = sources,
            ).asSuccess(reports)
        }
    }
}
