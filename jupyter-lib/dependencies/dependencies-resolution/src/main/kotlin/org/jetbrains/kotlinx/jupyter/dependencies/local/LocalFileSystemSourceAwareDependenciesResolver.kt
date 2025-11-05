package org.jetbrains.kotlinx.jupyter.dependencies.local

import org.jetbrains.kotlinx.jupyter.dependencies.api.ArtifactRequest
import org.jetbrains.kotlinx.jupyter.dependencies.api.Repository
import org.jetbrains.kotlinx.jupyter.dependencies.api.ResolvedArtifacts
import org.jetbrains.kotlinx.jupyter.dependencies.api.SourceAwareDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.util.makeResolutionFailureResult
import java.io.File
import java.net.URL
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess

/**
 * This class is used in the source code, but it's shadowed into a fat JAR beforehand,
 * so the IDE thinks it's unused
 */
@Suppress("unused")
class LocalFileSystemSourceAwareDependenciesResolver(
    vararg paths: File,
) : SourceAwareDependenciesResolver {
    private fun String.toRepositoryFileOrNull(): File? = File(this).takeIf { it.exists() && it.isDirectory }

    private fun String.toRepositoryUrlOrNull(): URL? =
        try {
            URL(this)
        } catch (_: Exception) {
            null
        }

    private fun Repository.toFilePath(): File? {
        val url = this.value.toRepositoryUrlOrNull()
        val path = if (url?.protocol == "file") url.path else this.value
        return path.toRepositoryFileOrNull()
    }

    /**
     * Returns true if the repository points to an existing local directory (or file:// URL).
     */
    override fun acceptsRepository(repository: Repository): Boolean = repository.toFilePath() != null

    /**
     * Adds a local repository directory to search for artifacts.
     */
    override fun addRepository(
        repository: Repository,
        sourceCodeLocation: SourceCode.LocationWithId?,
    ): ResultWithDiagnostics<Boolean> {
        if (!acceptsRepository(repository)) return false.asSuccess()

        val repoDir =
            repository.toFilePath()
                ?: return makeResolutionFailureResult(
                    "Invalid repository location: '${repository.value}'",
                    sourceCodeLocation,
                )

        localRepos.add(repoDir)

        return true.asSuccess()
    }

    /**
     * Accepts any non-blank path (interpreted relative to configured repositories or absolute).
     */
    override fun acceptsArtifact(artifactCoordinates: String) = artifactCoordinates.isNotBlank()

    /**
     * Resolves paths by checking them inside the configured local repositories (and the filesystem).
     */
    override suspend fun resolve(
        artifactRequests: List<ArtifactRequest>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts> {
        val messages = mutableListOf<String>()
        val binaries = mutableListOf<File>()

        for (artifactWithLocation in artifactRequests) {
            val (artifactCoordinates, sourceCodeLocation) = artifactWithLocation

            if (!acceptsArtifact(artifactCoordinates)) {
                return makeResolutionFailureResult("Path is invalid", sourceCodeLocation)
            }

            for (repo in localRepos) {
                val file = if (repo == null) File(artifactCoordinates) else File(repo, artifactCoordinates)
                when {
                    !file.exists() -> messages.add("File '$file' not found")
                    !file.isFile && !file.isDirectory -> messages.add("Path '$file' is neither file nor directory")
                    else -> {
                        binaries.add(file)
                        break
                    }
                }
            }
        }

        return if (binaries.isNotEmpty()) {
            ResolvedArtifacts(binaries = binaries).asSuccess()
        } else {
            // join all messages and report failure at the last seen location if available
            val lastLocation = artifactRequests.lastOrNull()?.sourceCodeLocation
            makeResolutionFailureResult(messages, lastLocation)
        }
    }

    private val localRepos = arrayListOf<File?>(null)

    init {
        for (path in paths) {
            require(path.exists() && path.isDirectory) { "Invalid flat lib directory repository path '$path'" }
        }
        localRepos.addAll(paths)
    }
}
