package org.jetbrains.kotlin.jupyter.dependencies

import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates

class RemoteResolverWrapper(private val remoteResolver: ExternalDependenciesResolver) :
    ExternalDependenciesResolver by remoteResolver {

    override fun acceptsRepository(repositoryCoordinates: RepositoryCoordinates): Boolean {
        return hasRepository(repositoryCoordinates) ||
            remoteResolver.acceptsRepository(repositoryCoordinates)
    }

    override fun addRepository(repositoryCoordinates: RepositoryCoordinates, options: ExternalDependenciesResolver.Options, sourceCodeLocation: SourceCode.LocationWithId?): ResultWithDiagnostics<Boolean> {
        val repository = getRepository(repositoryCoordinates) ?: repositoryCoordinates
        return remoteResolver.addRepository(repository, options, sourceCodeLocation)
    }

    companion object {
        private class Shortcut(val shortcut: String, pathGetter: () -> String) {
            val path = pathGetter()
        }

        private val HOME_PATH = System.getProperty("user.home") ?: "~"
        private const val PREFIX = "*"
        private val repositories: Map<String, Shortcut> =
            listOf(
                Shortcut("mavenLocal") {
                    // Simplified version, without looking in XML files
                    val path = System.getProperty("maven.repo.local")
                        ?: "$HOME_PATH/.m2/repository"
                    path.toURLString()
                },
                Shortcut("ivyLocal") {
                    val path = "$HOME_PATH/.ivy2/cache"
                    path.toURLString()
                },
            )
                .map {
                    "$PREFIX${it.shortcut}" to it
                }
                .toMap()

        fun hasRepository(repository: RepositoryCoordinates): Boolean {
            return repositories.containsKey(repository.string)
        }

        fun getRepository(repository: RepositoryCoordinates): RepositoryCoordinates? {
            return repositories[repository.string]?.path?.let { RepositoryCoordinates(it) }
        }

        private fun String.toURLString(): String {
            return File(this).toURI().toURL().toString()
        }
    }
}
