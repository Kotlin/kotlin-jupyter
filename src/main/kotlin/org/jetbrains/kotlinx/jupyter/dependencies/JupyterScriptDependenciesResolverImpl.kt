package org.jetbrains.kotlinx.jupyter.dependencies

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.config.getLogger
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver.Options
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.impl.DependenciesResolverOptionsName
import kotlin.script.experimental.dependencies.impl.makeExternalDependenciesResolverOptions
import kotlin.script.experimental.dependencies.impl.set
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

open class JupyterScriptDependenciesResolverImpl(mavenRepositories: List<RepositoryCoordinates>) : JupyterScriptDependenciesResolver {

    private val log = getLogger("resolver")

    private val resolver: ExternalDependenciesResolver
    private val resolverOptions = buildOptions(
        DependenciesResolverOptionsName.SCOPE to "compile,runtime"
    )

    private val sourcesResolverOptions = buildOptions(
        DependenciesResolverOptionsName.PARTIAL_RESOLUTION to "true",
        DependenciesResolverOptionsName.SCOPE to "compile,runtime",
        DependenciesResolverOptionsName.CLASSIFIER to "sources",
        DependenciesResolverOptionsName.EXTENSION to "jar",
    )

    private val repositories = arrayListOf<Repo>()
    private val addedClasspath = arrayListOf<File>()

    override var resolveSources: Boolean = false
    private val addedSourcesClasspath = arrayListOf<File>()

    init {
        resolver = CompoundDependenciesResolver(
            FileSystemDependenciesResolver(),
            RemoteResolverWrapper(MavenDependenciesResolver())
        )
        mavenRepositories.forEach { addRepository(Repo(it)) }
    }

    private fun buildOptions(vararg options: Pair<DependenciesResolverOptionsName, String>): Options {
        return makeExternalDependenciesResolverOptions(
            mutableMapOf<String, String>().apply {
                for (option in options) this[option.first] = option.second
            }
        )
    }

    private fun addRepository(repo: Repo): Boolean {
        val repoIndex = repositories.indexOfFirst { it.coordinates.string == repo.coordinates.string }
        if (repoIndex != -1) repositories.removeAt(repoIndex)
        repositories.add(repo)

        return resolver.addRepository(repo.coordinates, repo.options).valueOrNull() == true
    }

    override fun popAddedClasspath(): List<File> {
        val result = addedClasspath.toList()
        addedClasspath.clear()
        return result
    }

    override fun popAddedSources(): List<File> {
        val result = addedSourcesClasspath.toList()
        addedSourcesClasspath.clear()
        return result
    }

    override fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>> {
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()
        val classpath = mutableListOf<File>()
        var existingRepositories: List<Repo>? = null

        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    log.info("Adding repository: ${annotation.value}")
                    if (existingRepositories == null) {
                        existingRepositories = ArrayList(repositories)
                    }

                    val options = if (annotation.username.isNotEmpty() || annotation.password.isNotEmpty()) {
                        buildOptions(
                            DependenciesResolverOptionsName.USERNAME to annotation.username,
                            DependenciesResolverOptionsName.PASSWORD to annotation.password,
                        )
                    } else {
                        Options.Empty
                    }
                    val repo = Repo(RepositoryCoordinates(annotation.value), options)

                    if (!addRepository(repo)) {
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                    }

                    existingRepositories?.forEach { addRepository(it) }
                }
                is DependsOn -> {
                    log.info("Resolving ${annotation.value}")
                    try {
                        doResolve(
                            { resolver.resolve(annotation.value, resolverOptions) },
                            onResolved = { files ->
                                addedClasspath.addAll(files)
                                classpath.addAll(files)
                            },
                            onFailure = { result ->
                                val diagnostics = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Failed to resolve ${annotation.value}:\n" + result.reports.joinToString("\n") { it.message })
                                log.warn(diagnostics.message, diagnostics.exception)
                                scriptDiagnostics.add(diagnostics)
                            }
                        )

                        if (resolveSources) {
                            doResolve(
                                { resolver.resolve(annotation.value, sourcesResolverOptions) },
                                onResolved = { files ->
                                    addedSourcesClasspath.addAll(files)
                                },
                                onFailure = { result ->
                                    log.warn("Failed to resolve sources for ${annotation.value}:\n" + result.reports.joinToString("\n") { it.message })
                                }
                            )
                        }
                    } catch (e: Exception) {
                        val diagnostic = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unhandled exception during resolve", exception = e)
                        log.error(diagnostic.message, e)
                        scriptDiagnostics.add(diagnostic)
                    }
                    // Hack: after first resolution add "standard" Central repo to the end of the list, giving it the lowest priority
                    addRepository(CENTRAL_REPO)
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }

        return if (scriptDiagnostics.isEmpty()) classpath.asSuccess()
        else makeFailureResult(scriptDiagnostics)
    }

    private fun doResolve(
        resolveAction: suspend () -> ResultWithDiagnostics<List<File>>,
        onResolved: (List<File>) -> Unit,
        onFailure: (ResultWithDiagnostics.Failure) -> Unit
    ) {
        when (val result = runBlocking { resolveAction() }) {
            is ResultWithDiagnostics.Failure -> {
                onFailure(result)
            }
            is ResultWithDiagnostics.Success -> {
                log.info("Resolved: " + result.value.joinToString())
                onResolved(result.value)
            }
        }
    }

    private class Repo(
        val coordinates: RepositoryCoordinates,
        val options: Options = Options.Empty
    )

    companion object {
        private val CENTRAL_REPO_COORDINATES = RepositoryCoordinates("https://repo1.maven.org/maven2/")
        private val CENTRAL_REPO = Repo(CENTRAL_REPO_COORDINATES)
    }
}
