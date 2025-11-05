package org.jetbrains.kotlinx.jupyter.dependencies.maven.resolution

import org.jetbrains.amper.dependency.resolution.AmperDependencyResolutionException
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionState
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.RootCacheEntryKey
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.kotlinx.jupyter.dependencies.api.ArtifactRequest
import org.jetbrains.kotlinx.jupyter.dependencies.maven.artifacts.toMavenArtifact
import org.jetbrains.kotlinx.jupyter.dependencies.maven.dependencyCollection.MavenDependencyCollector
import org.jetbrains.kotlinx.jupyter.dependencies.util.makeResolutionFailureResult
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess

/**
 * Resolves and collects Maven artifacts using Amper's resolver and reports them to [dependencyCollector].
 *
 * - Only starts resolution if there is at least one "current" artifact; otherwise returns success immediately.
 * - Builds a dependency graph for all provided artifacts, but collects only the subgraph reachable
 *   from the current artifacts (so transitive dependencies of unrelated artifacts are ignored).
 * - Honors [resolveSources]: when true, source artifacts returned by Amper are added as sources.
 * - Any [AmperDependencyResolutionException] (i.e., unresolved dependency) thrown during resolution is
 *   transformed into a Kotlin scripting failure with a concise diagnostic
 *   tied to [ArtifactRequest.sourceCodeLocation].
 */
internal suspend fun doAmperResolve(
    resolutionContext: Context,
    currentArtifactsWithLocations: Collection<ArtifactRequest>,
    allArtifactsWithLocations: Collection<ArtifactRequest>,
    resolveSources: Boolean,
    dependencyCollector: MavenDependencyCollector,
): ResultWithDiagnostics<Unit> {
    val firstArtifactWithLocation =
        currentArtifactsWithLocations.firstOrNull()
            ?: return Unit.asSuccess()

    return try {
        val currentArtifactCoordinates = mutableSetOf<MavenCoordinates>()
        val resolvedGraph =
            resolveDependencies(
                currentArtifactCoordinates,
                currentArtifactsWithLocations,
                allArtifactsWithLocations,
                resolutionContext,
                resolveSources,
            )
        collectResolvedArtifacts(dependencyCollector, resolvedGraph, currentArtifactCoordinates, resolveSources)

        Unit.asSuccess()
    } catch (e: AmperDependencyResolutionException) {
        makeAmperResolutionFailureResult(e, firstArtifactWithLocation.sourceCodeLocation)
    }
}

/**
 * Builds a root node from all requested artifacts and delegates dependency resolution to Amper.
 *
 * Important:
 * - [currentArtifactCoordinates] is populated with coordinates of artifacts that belong to the
 *   current request set; this is later used to filter the resolved graph for collection.
 * - The created root uses [RootCacheEntryKey.FromChildren] so the cache key depends solely on the
 *   children nodes, which enables reuse across requests with the same initial set.
 * - If [resolveSources] is true, sources/Javadoc may be downloaded along with binaries.
 *   Javadocs are later ignored, but we can't disable them exclusively, so we download them anyway.
 */
private suspend fun resolveDependencies(
    currentArtifactCoordinates: MutableSet<MavenCoordinates>,
    currentArtifactsWithLocations: Collection<ArtifactRequest>,
    allArtifactsWithLocations: Collection<ArtifactRequest>,
    resolutionContext: Context,
    resolveSources: Boolean,
): DependencyNode {
    val currentArtifactStrings =
        currentArtifactsWithLocations
            .map { it.artifact }
            .toSet()

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

    return Resolver().resolveDependencies(
        root,
        downloadSources = resolveSources,
    )
}

/**
 * Traverses the resolved dependency graph and reports files to [dependencyCollector].
 *
 * Behavior and caveats:
 * - Starts from nodes that correspond to the current artifacts and performs a distinct BFS to
 *   avoid duplicates when the graph has converging edges.
 * - Throws [AmperDependencyResolutionException] if a dependency node is unresolved, except for
 *   a known false-positive excluded dependency (see [shouldIgnoreUnresolvedDependencyForRequest]).
 */
private fun collectResolvedArtifacts(
    dependencyCollector: MavenDependencyCollector,
    resolvedGraph: DependencyNode,
    currentArtifactCoordinates: Collection<MavenCoordinates>,
    resolveSources: Boolean,
) {
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
}

/**
 * Converts any thrown [exception] during resolution into a scripting [ResultWithDiagnostics.Failure].
 *
 * Selection of message:
 * - If the cause chain contains an [AmperDependencyResolutionException], its class and message are
 *   preferred as they best reflect dependency issues for the user.
 * - Otherwise, the top-level exception is used.
 *
 * The resulting diagnostics are associated with [location] (if provided) to highlight
 * the exact cell/snippet that initiated the resolution.
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
 * Workaround for AMPER-923: ignore a known non-existent, excluded transitive dependency that appears
 * in the graph of deeplearning4j-ui. Without this, resolution would incorrectly fail on an
 * intentionally excluded module: `org.webjars.npm:coreui__coreui-plugin-npm-postinstall`.
 */
private fun shouldIgnoreUnresolvedDependencyForRequest(request: Collection<MavenCoordinates>): Boolean =
    request.any {
        it.groupId == "org.deeplearning4j" && it.artifactId == "deeplearning4j-ui"
    }
