package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.amper.dependency.resolution.AmperDependencyResolutionException
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.ResolutionState
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.RootCacheEntryKey
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.incrementalcache.IncrementalCache
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.valueOrNull
import org.jetbrains.amper.dependency.resolution.Repository as AmperRepository

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

    // Deprioritize Central repo, prioritize Maven Local, avoid duplicates
    private val repos =
        TreeSet<AmperRepository>(
            compareBy { repo ->
                when (repo) {
                    is MavenRepository -> {
                        when (repo.url) {
                            CENTRAL_REPO.value -> 100
                            else -> 1
                        }
                    }
                    is MavenLocal -> {
                        -100
                    }
                }
            },
        )

    private val requestedArtifacts = mutableMapOf<String, ArtifactRequest>()
    private val dependencyCollector = DependencyCollector(OldestWinsVersionConflictResolutionStrategy)

    /**
     * Returns true if the repository URL looks valid for Maven resolution.
     */
    override fun acceptsRepository(repository: Repository): Boolean =
        repository.value == MAVEN_LOCAL_NAME || repository.value.toRepositoryUrlOrNull() != null

    /**
     * Adds a Maven repository to be used during resolution. Username/password may be taken from
     * environment variables if specified with the special syntax, see [tryResolveEnvironmentVariable].
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
            verifyChecksumsLocally = false
            incrementalCache =
                IncrementalCache(
                    incrementalCachePath,
                    amperVersion,
                )
        }
}

private suspend fun doAmperResolve(
    resolutionContext: Context,
    currentArtifactsWithLocations: Collection<ArtifactRequest>,
    allArtifactsWithLocations: Collection<ArtifactRequest>,
    resolveSources: Boolean,
    dependencyCollector: DependencyCollector,
): ResultWithDiagnostics<Unit> {
    val firstArtifactWithLocation =
        currentArtifactsWithLocations.firstOrNull()
            ?: return Unit.asSuccess()

    try {
        val currentArtifactStrings =
            currentArtifactsWithLocations
                .map { it.artifact }
                .toSet()

        val currentArtifactCoordinates = mutableSetOf<MavenCoordinates>()

        val childrenNodes =
            allArtifactsWithLocations.map {
                val artifactString = it.artifact
                val artifactCoordinates = artifactString.toMavenArtifact()!!
                if (artifactString in currentArtifactStrings) {
                    currentArtifactCoordinates.add(artifactCoordinates)
                }
                resolutionContext.toMavenDependencyNode(artifactCoordinates)
            }

        val root =
            RootDependencyNodeWithContext(
                templateContext = resolutionContext,
                rootCacheEntryKey = RootCacheEntryKey.FromChildren,
                children = childrenNodes,
            )

        val resolvedGraph =
            Resolver().resolveDependencies(
                root,
                downloadSources = resolveSources,
            )

        val nodeSequence =
            resolvedGraph.children
                .asSequence()
                .filterIsInstance<MavenDependencyNode>()
                .filter { it.dependency.coordinates in currentArtifactCoordinates }
                .flatMap { it.distinctBfsSequence() }
                .filterIsInstance<MavenDependencyNode>()
                .distinct()

        for (node in nodeSequence) {
            val dependency = node.dependency
            if (dependency.state != ResolutionState.RESOLVED) {
                if (shouldIgnoreUnresolvedDependencyForRequest(currentArtifactCoordinates)) {
                    continue
                }
                throw AmperDependencyResolutionException(
                    "Dependency '${dependency.coordinates}' was not resolved",
                )
            }

            for (file in dependency.files(withSources = resolveSources)) {
                val path = file.path ?: continue
                if (!path.exists()) {
                    if (file.isDocumentation) continue
                    throw AmperDependencyResolutionException(
                        "File '$path' for dependency '${dependency.coordinates}' " +
                            "was returned by resolution, but does not exist on the filesystem",
                    )
                }

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

        return Unit.asSuccess()
    } catch (e: AmperDependencyResolutionException) {
        return makeAmperResolutionFailureResult(e, firstArtifactWithLocation.sourceCodeLocation)
    }
}

private fun String.toRepositoryUrlOrNull(): URL? =
    try {
        URL(this)
    } catch (_: MalformedURLException) {
        null
    }

private fun String.toMavenArtifact(): MavenCoordinates? {
    val gradleMavenCoordinates = parseGradleCoordinatesString(this) ?: return null
    return with(gradleMavenCoordinates) {
        MavenCoordinates(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
        )
    }
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

/**
 * Creates a failure result from an [exception], extracting a concise message from the primary cause
 * (preferring [AmperDependencyResolutionException] if present).
 */
private fun makeAmperResolutionFailureResult(
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

    return makeResolutionFailureResult(listOf(message), location, exception)
}

/**
 * It's a workaround for https://youtrack.jetbrains.com/issue/AMPER-923/
 * deeplearning4j-ui has a transitive but excluded dependency on `org.webjars.npm:coreui__coreui-plugin-npm-postinstall`
 * which is in fact non-existent. So we just ignore it.
 */
private fun shouldIgnoreUnresolvedDependencyForRequest(request: Collection<MavenCoordinates>): Boolean =
    request.any {
        it.groupId == "org.deeplearning4j" && it.artifactId == "deeplearning4j-ui"
    }

private fun Repository.convertToAmperRepository(sourceCodeLocation: SourceCode.LocationWithId?): ResultWithDiagnostics<AmperRepository?> {
    if (value == MAVEN_LOCAL_NAME) {
        return MavenLocal.asSuccess()
    }

    val url = value.toRepositoryUrlOrNull() ?: return null.asSuccess()

    val reports = mutableListOf<ScriptDiagnostic>()

    fun getFinalValue(
        optionName: String,
        rawValue: String?,
    ): String? =
        tryResolveEnvironmentVariable(rawValue, optionName, sourceCodeLocation)
            .onFailure { reports.addAll(it.reports) }
            .valueOrNull()

    val usernameSubstituted = getFinalValue("username", username)
    val passwordSubstituted = getFinalValue("password", password)

    if (reports.isNotEmpty()) {
        return ResultWithDiagnostics.Failure(reports)
    }

    return MavenRepository(
        url.toString(),
        usernameSubstituted,
        passwordSubstituted,
    ).asSuccess()
}
