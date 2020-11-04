package org.jetbrains.kotlin.jupyter

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.config.DependsOn
import org.jetbrains.kotlin.jupyter.config.Repository
import org.jetbrains.kotlin.mainKts.impl.IvyResolver
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.impl.DependenciesResolverOptionsName
import kotlin.script.experimental.dependencies.impl.makeExternalDependenciesResolverOptions

open class JupyterScriptDependenciesResolver(resolverConfig: ResolverConfig?) {

    private val log by lazy { LoggerFactory.getLogger("resolver") }

    private val resolver: ExternalDependenciesResolver
    private val resolverOptions = makeExternalDependenciesResolverOptions(
        mapOf(
            DependenciesResolverOptionsName.SCOPE.key to "compile,runtime"
        )
    )

    private val repositories = arrayListOf<RepositoryCoordinates>()
    private val addedClasspath = arrayListOf<File>()

    init {
        resolver = CompoundDependenciesResolver(
            FileSystemDependenciesResolver(),
            RemoteResolverWrapper(IvyResolver())
        )
        resolverConfig?.repositories?.forEach { addRepository(it) }
    }

    private fun addRepository(repository: RepositoryCoordinates): Boolean {
        val repoIndex = repositories.indexOfFirst { it.string == repository.string }
        if (repoIndex != -1) repositories.removeAt(repoIndex)
        repositories.add(repository)

        return resolver.addRepository(repository).valueOrNull() == true
    }

    fun popAddedClasspath(): List<File> {
        val result = addedClasspath.toList()
        addedClasspath.clear()
        return result
    }

    fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>> {
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()
        val classpath = mutableListOf<File>()
        var existingRepositories: List<RepositoryCoordinates>? = null

        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    log.info("Adding repository: ${annotation.value}")
                    if (existingRepositories == null) {
                        existingRepositories = ArrayList(repositories)
                    }

                    if (!addRepository(RepositoryCoordinates(annotation.value)))
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")

                    existingRepositories?.forEach { addRepository(it) }
                }
                is DependsOn -> {
                    log.info("Resolving ${annotation.value}")
                    try {
                        when (val result = runBlocking { resolver.resolve(annotation.value, resolverOptions) }) {
                            is ResultWithDiagnostics.Failure -> {
                                val diagnostics = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Failed to resolve ${annotation.value}:\n" + result.reports.joinToString("\n") { it.message })
                                log.warn(diagnostics.message, diagnostics.exception)
                                scriptDiagnostics.add(diagnostics)
                            }
                            is ResultWithDiagnostics.Success -> {
                                log.info("Resolved: " + result.value.joinToString())
                                addedClasspath.addAll(result.value)
                                classpath.addAll(result.value)
                            }
                        }
                    } catch (e: Exception) {
                        val diagnostic = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unhandled exception during resolve", exception = e)
                        log.error(diagnostic.message, e)
                        scriptDiagnostics.add(diagnostic)
                    }
                    // Hack: after first resolution add "standard" Central repo to the end of the list, giving it the lowest priority
                    addRepository(CENTRAL_REPO_COORDINATES)
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }

        return if (scriptDiagnostics.isEmpty()) classpath.asSuccess()
        else makeFailureResult(scriptDiagnostics)
    }

    companion object {
        val CENTRAL_REPO_COORDINATES = RepositoryCoordinates("https://repo1.maven.org/maven2/")
    }
}

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
                    "${PREFIX}${it.shortcut}" to it
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
