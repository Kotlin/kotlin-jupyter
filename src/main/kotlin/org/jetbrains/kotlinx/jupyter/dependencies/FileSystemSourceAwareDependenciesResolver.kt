package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import java.io.File
import java.net.URL
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.impl.makeResolveFailureResult

class FileSystemSourceAwareDependenciesResolver(
    vararg paths: File,
) : SourceAwareDependenciesResolver {
    private fun String.toRepositoryFileOrNull(): File? = File(this).takeIf { it.exists() && it.isDirectory }

    private fun String.toRepositoryUrlOrNull(): URL? =
        try {
            URL(this)
        } catch (_: Exception) {
            null
        }

    private fun RepositoryDescription.toFilePath(): File? {
        val url = this.value.toRepositoryUrlOrNull()
        val path = if (url?.protocol == "file") url.path else this.value
        return path.toRepositoryFileOrNull()
    }

    override fun acceptsRepository(repository: RepositoryDescription): Boolean = repository.toFilePath() != null

    override fun addRepository(
        repository: RepositoryDescription,
        sourceCodeLocation: SourceCode.LocationWithId?,
    ): ResultWithDiagnostics<Boolean> {
        if (!acceptsRepository(repository)) return false.asSuccess()

        val repoDir =
            repository.toFilePath()
                ?: return makeResolveFailureResult("Invalid repository location: '${'$'}{repository.value}'", sourceCodeLocation)

        localRepos.add(repoDir)

        return true.asSuccess()
    }

    override fun acceptsArtifact(artifactCoordinates: String) = artifactCoordinates.isNotBlank()

    override suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        resolveSources: Boolean,
    ): ResultWithDiagnostics<ResolvedArtifacts> {
        val messages = mutableListOf<String>()
        val binaries = mutableListOf<File>()

        for (artifactWithLocation in artifactsWithLocations) {
            val (artifactCoordinates, sourceCodeLocation) = artifactWithLocation

            if (!acceptsArtifact(artifactCoordinates)) {
                return makeResolveFailureResult("Path is invalid", sourceCodeLocation)
            }

            for (repo in localRepos) {
                // TODO: add coordinates and wildcard matching
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
            val lastLocation = artifactsWithLocations.lastOrNull()?.sourceCodeLocation
            makeResolveFailureResult(messages, lastLocation)
        }
    }

    private val localRepos = arrayListOf<File?>(null)

    init {
        for (path in paths) {
            require(path.exists() && path.isDirectory) { "Invalid flat lib directory repository path '${'$'}path'" }
        }
        localRepos.addAll(paths)
    }
}
