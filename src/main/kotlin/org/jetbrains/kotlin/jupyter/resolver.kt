package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.mainKts.impl.IvyResolver
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates

open class JupyterScriptDependenciesResolver(resolverConfig: ResolverConfig?) {

    private val log by lazy { LoggerFactory.getLogger("resolver") }

    private val resolver: ExternalDependenciesResolver

    init {
        resolver = CompoundDependenciesResolver(
                FileSystemDependenciesResolver(),
                RemoteResolverWrapper(IvyResolver())
        )
        resolverConfig?.repositories?.forEach { resolver.addRepository(it) }
    }

    private val addedClasspath: MutableList<File> = mutableListOf()

    fun popAddedClasspath(): List<File> {
        val result = addedClasspath.toList()
        addedClasspath.clear()
        return result
    }

    fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>> {
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()
        val classpath = mutableListOf<File>()

        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    log.info("Adding repository: ${annotation.value}")
                    if (resolver.addRepository(RepositoryCoordinates(annotation.value)).valueOrNull() != true)
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                }
                is DependsOn -> {
                    log.info("Resolving ${annotation.value}")
                    try {
                        val result = runBlocking { resolver.resolve(annotation.value) }
                        when (result) {
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
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        return if (scriptDiagnostics.isEmpty()) classpath.asSuccess()
        else makeFailureResult(scriptDiagnostics)
    }
}

class RemoteResolverWrapper(private val remoteResolver: ExternalDependenciesResolver):
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