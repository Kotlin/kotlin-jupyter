package org.jetbrains.kotlinx.jupyter.dependencies

import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess

/**
 * A compound resolver that composes multiple SourceAwareDependenciesResolver implementations.
 *
 * This class is used in the source code, but it's shadowed into a fat JAR beforehand,
 * so the IDE thinks it's unused
 */
@Suppress("unused")
class CompoundSourceAwareDependenciesResolver(
    val resolvers: List<SourceAwareDependenciesResolver>,
) : SourceAwareDependenciesResolver {
    constructor(vararg resolvers: SourceAwareDependenciesResolver) : this(resolvers.toList())

    /**
     * Returns true if any underlying resolver accepts the coordinates.
     */
    override fun acceptsArtifact(artifactCoordinates: String): Boolean = resolvers.any { it.acceptsArtifact(artifactCoordinates) }

    /** Returns true if any underlying resolver recognizes the repository descriptor. */
    override fun acceptsRepository(repository: Repository): Boolean = resolvers.any { it.acceptsRepository(repository) }

    /**
     * For each underlying resolver that accepts the repository, delegates the call and aggregates diagnostics.
     * Returns success if at least one resolver succeeded.
     */
    override fun addRepository(
        repository: Repository,
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
                makeResolutionFailureResult(
                    "No dependency resolver found that recognizes the repository coordinates '$repository'",
                    sourceCodeLocation,
                )
            else -> ResultWithDiagnostics.Failure(reports)
        }
    }

    /**
     * Resolves each artifact by trying underlying resolvers in order, stopping at the first success per artifact,
     * and aggregating results and diagnostics.
     */
    override suspend fun resolve(
        artifactRequests: List<ArtifactRequest>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts> {
        val reports = mutableListOf<ScriptDiagnostic>()
        val binaries = mutableListOf<File>()
        val sources = mutableListOf<File>()
        var hadErrors = false

        // For each artifact, try resolvers in order. Aggregate diagnostics; stop at the first success per artifact.
        for (artifactWithLocation in artifactRequests) {
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
                            message = "No suitable dependency resolver found for artifact '$artifact'",
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
