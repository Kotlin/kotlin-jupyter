package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.amper.dependency.resolution.AmperDependencyResolutionException
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.ResolutionState
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import java.net.MalformedURLException
import java.net.URL
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.impl.makeResolveFailureResult

class AmperMavenDependenciesResolver : SourceAwareDependenciesResolver {
    private val repos = mutableListOf<MavenRepository>()
    private val requestedArtifacts = mutableMapOf<String, ArtifactWithLocation>()
    private val dependencyCollector = DependencyCollector(OldestWinsVersionConflictResolutionStrategy)

    override fun acceptsRepository(repository: RepositoryDescription): Boolean = repository.value.toRepositoryUrlOrNull() != null

    override fun addRepository(
        repository: RepositoryDescription,
        sourceCodeLocation: SourceCode.LocationWithId?,
    ): ResultWithDiagnostics<Boolean> {
        val url =
            repository.value.toRepositoryUrlOrNull()
                ?: return false.asSuccess()

        val usernameRaw = repository.username
        val passwordRaw = repository.password

        val reports = mutableListOf<ScriptDiagnostic>()

        fun getFinalValue(
            optionName: String,
            rawValue: String?,
        ): String? =
            tryResolveEnvironmentVariable(rawValue, optionName, sourceCodeLocation)
                .onFailure { reports.addAll(it.reports) }
                .valueOrNull()

        val username = getFinalValue("username", usernameRaw)
        val password = getFinalValue("password", passwordRaw)

        if (reports.isNotEmpty()) {
            return ResultWithDiagnostics.Failure(reports)
        }

        repos.add(
            MavenRepository(
                url.toString(),
                username,
                password,
            ),
        )
        return true.asSuccess()
    }

    override fun acceptsArtifact(artifactCoordinates: String): Boolean = artifactCoordinates.toMavenArtifact() != null

    override suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts> {
        for (artifact in artifactsWithLocations) {
            requestedArtifacts[artifact.artifact] = artifact
        }
        val result =
            doAmperResolve(
                requestedArtifacts.values,
                resolveSources,
                repos,
                dependencyCollector,
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
}

private suspend fun doAmperResolve(
    artifactsWithLocations: Collection<ArtifactWithLocation>,
    resolveSources: Boolean,
    repos: List<MavenRepository>,
    dependencyCollector: DependencyCollector,
): ResultWithDiagnostics<Unit> {
    val resolutionContext =
        Context {
            scope = ResolutionScope.RUNTIME
            platforms = setOf(ResolutionPlatform.JVM)
            repositories = repos
        }

    val firstArtifactWithLocation =
        artifactsWithLocations.firstOrNull()
            ?: return Unit.asSuccess()

    try {
        val artifactIds =
            artifactsWithLocations.map {
                it.artifact.toMavenArtifact()!!
            }

        val root =
            RootDependencyNodeWithContext(
                templateContext = resolutionContext,
                children =
                    artifactIds.map {
                        resolutionContext.toMavenDependencyNode(it)
                    },
            )

        val resolvedGraph =
            Resolver().resolveDependencies(
                root,
                downloadSources = resolveSources,
            )

        for (node in resolvedGraph.distinctBfsSequence()) {
            if (node is MavenDependencyNode) {
                val dependency = node.dependency
                if (dependency.state != ResolutionState.RESOLVED) {
                    throw AmperDependencyResolutionException(
                        "Dependency '${dependency.coordinates}' was not resolved",
                    )
                }

                for (file in dependency.files(withSources = resolveSources)) {
                    val path = file.path ?: continue
                    if (!path.exists()) continue

                    when {
                        file.isDocumentation -> {
                            if (!path.name.endsWith("-javadoc.jar")) {
                                dependencyCollector.addSource(dependency.coordinates, path.toFile())
                            }
                        }
                        else -> {
                            dependencyCollector.addBinary(dependency.coordinates, path.toFile())
                        }
                    }
                }
            }
        }

        return Unit.asSuccess()
    } catch (e: AmperDependencyResolutionException) {
        return makeResolveFailureResult(e, firstArtifactWithLocation.sourceCodeLocation)
    }
}

private fun String.toRepositoryUrlOrNull(): URL? =
    try {
        URL(this)
    } catch (_: MalformedURLException) {
        null
    }

private fun String.toMavenArtifact(): MavenCoordinates? {
    val dependencyParts = split(":")
    if (dependencyParts.size !in 3..5) {
        return null
    }

    val groupId = dependencyParts[0]
    val artifactId = dependencyParts[1]
    val version = dependencyParts[2]
    val classifier = dependencyParts.getOrNull(3)

    return MavenCoordinates(
        groupId,
        artifactId,
        version,
        classifier,
    )
}

private fun tryResolveEnvironmentVariable(
    str: String?,
    optionName: String,
    location: SourceCode.LocationWithId?,
): ResultWithDiagnostics<String?> {
    if (str == null) return null.asSuccess()
    if (!str.startsWith("$")) return str.asSuccess()
    val envName = str.substring(1)
    val envValue: String? = System.getenv(envName)
    if (envValue.isNullOrEmpty()) {
        return ResultWithDiagnostics.Failure(
            ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedError,
                "Environment variable `$envName` for $optionName is not set",
                ScriptDiagnostic.Severity.ERROR,
                location,
            ),
        )
    }
    return envValue.asSuccess()
}

private fun makeResolveFailureResult(
    exception: Throwable,
    location: SourceCode.LocationWithId?,
): ResultWithDiagnostics.Failure {
    val allCauses = generateSequence(exception) { e: Throwable -> e.cause }.toList()
    val primaryCause = allCauses.firstOrNull { it is AmperDependencyResolutionException } ?: exception

    val message =
        buildString {
            append(primaryCause::class.simpleName)
            if (primaryCause.message != null) {
                append(": ")
                append(primaryCause.message)
            }
        }

    return makeResolveFailureResult(listOf(message), location, exception)
}
