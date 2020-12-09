package org.jetbrains.kotlin.jupyter.dependencies

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.config.getLogger
import org.jetbrains.kotlin.mainKts.impl.IvyResolver
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
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

    private val log = getLogger("resolver")

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

                    if (!addRepository(RepositoryCoordinates(annotation.value))) {
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                    }

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
