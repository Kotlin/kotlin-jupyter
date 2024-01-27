package org.jetbrains.kotlinx.jupyter.dependencies

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.resolvePath
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver.Options
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.impl.DependenciesResolverOptionsName
import kotlin.script.experimental.dependencies.impl.makeExternalDependenciesResolverOptions
import kotlin.script.experimental.dependencies.impl.set
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

open class JupyterScriptDependenciesResolverImpl(mavenRepositories: List<MavenRepositoryCoordinates>) : JupyterScriptDependenciesResolver {

    private val log = getLogger("resolver")

    private val resolver: ExternalDependenciesResolver
    private val resolverOptions = buildOptions(
        DependenciesResolverOptionsName.SCOPE to "compile,runtime",
    )

    private val sourcesResolverOptions = buildOptions(
        DependenciesResolverOptionsName.PARTIAL_RESOLUTION to "true",
        DependenciesResolverOptionsName.SCOPE to "compile,runtime",
        DependenciesResolverOptionsName.CLASSIFIER to "sources",
        DependenciesResolverOptionsName.EXTENSION to "jar",
    )

    private val mppResolverOptions = buildOptions(
        DependenciesResolverOptionsName.PARTIAL_RESOLUTION to "true",
        DependenciesResolverOptionsName.SCOPE to "compile,runtime",
        DependenciesResolverOptionsName.EXTENSION to "module",
    )

    private val repositories = arrayListOf<Repo>()
    private val addedClasspath = arrayListOf<File>()

    override var resolveSources: Boolean = false
    override var resolveMpp: Boolean = false
    private val addedSourcesClasspath = arrayListOf<File>()

    init {
        resolver = CompoundDependenciesResolver(
            FileSystemDependenciesResolver(),
            RemoteResolverWrapper(MavenDependenciesResolver(true)),
        )
        mavenRepositories.forEach { addRepository(Repo(it)) }
    }

    private fun buildOptions(vararg options: Pair<DependenciesResolverOptionsName, String>): Options {
        return makeExternalDependenciesResolverOptions(
            mutableMapOf<String, String>().apply {
                for (option in options) this[option.first] = option.second
            },
        )
    }

    private fun addRepository(repo: Repo): Boolean {
        val repoIndex = repositories.indexOfFirst { it.repo == repo.repo }
        if (repoIndex != -1) repositories.removeAt(repoIndex)
        repositories.add(repo)

        return resolver.addRepository(RepositoryCoordinates(repo.repo.coordinates), repo.options, null).valueOrNull() == true
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
        val dependsOnAnnotations = mutableListOf<String>()

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
                    val repo = Repo(MavenRepositoryCoordinates(annotation.value), options)

                    if (!addRepository(repo)) {
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                    }

                    existingRepositories?.forEach { addRepository(it) }
                }
                is DependsOn -> {
                    dependsOnAnnotations.add(annotation.value)
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }

        try {
            tryResolve(
                dependsOnAnnotations,
                scriptDiagnostics,
                classpath,
            )
        } catch (e: Exception) {
            val diagnostic = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unhandled exception during resolve", exception = e)
            log.error(diagnostic.message, e)
            scriptDiagnostics.add(diagnostic)
        }
        // Hack: after first resolution add "standard" Central repo to the end of the list, giving it the lowest priority
        addRepository(CENTRAL_REPO)

        return makeResolutionResult(classpath, scriptDiagnostics)
    }

    private suspend fun resolveWithOptions(annotationArgs: List<String>, options: Options): ResultWithDiagnostics<List<File>> {
        return resolver.resolve(annotationArgs.map { ArtifactWithLocation(it, null) }, options)
    }

    private fun tryResolve(
        annotationArgs: List<String>,
        scriptDiagnosticsResult: MutableList<ScriptDiagnostic>,
        classpathResult: MutableList<File>,
    ) {
        if (annotationArgs.isEmpty()) return

        log.info("Resolving $annotationArgs")
        doResolve(
            { resolveWithOptions(annotationArgs, resolverOptions) },
            onResolved = { files ->
                addedClasspath.addAll(files)
                classpathResult.addAll(files)
            },
            onFailure = { result ->
                val diagnostics = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Failed to resolve $annotationArgs:\n" + result.reports.joinToString("\n") { it.message })
                log.warn(diagnostics.message, diagnostics.exception)
                scriptDiagnosticsResult.add(diagnostics)
            },
        )

        if (resolveSources) {
            doResolve(
                { resolveWithOptions(annotationArgs, sourcesResolverOptions) },
                onResolved = { files ->
                    addedSourcesClasspath.addAll(files)
                },
                onFailure = { result ->
                    log.warn("Failed to resolve sources for $annotationArgs:\n" + result.reports.joinToString("\n") { it.message })
                },
            )
        }

        if (resolveMpp) {
            doResolve(
                { resolveWithOptions(annotationArgs, mppResolverOptions) },
                onResolved = { files ->
                    val resolvedArtifacts = mutableSetOf<String>()
                    resolvedArtifacts.addAll(annotationArgs)
                    resolveMpp(files) { artifactCoordinates ->
                        val notYetResolvedArtifacts = artifactCoordinates.filter { artifact ->
                            resolvedArtifacts.add(artifact)
                        }

                        tryResolve(
                            notYetResolvedArtifacts,
                            scriptDiagnosticsResult,
                            classpathResult,
                        )
                    }
                },
                onFailure = {},
            )
        }
    }

    private fun doResolve(
        resolveAction: suspend () -> ResultWithDiagnostics<List<File>>,
        onResolved: (List<File>) -> Unit,
        onFailure: (ResultWithDiagnostics.Failure) -> Unit,
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

    private fun resolveMpp(moduleFiles: List<File>, jvmArtifactCallback: (List<String>) -> Unit) {
        val coordinates = mutableListOf<String>()

        for (moduleFile in moduleFiles) {
            val json = try {
                Json.parseToJsonElement(moduleFile.readText())
            } catch (e: Throwable) {
                continue
            }

            val variants = (json.resolvePath(listOf("variants")) as? JsonArray) ?: continue
            for (v in variants) {
                val attrs = (v.resolvePath(listOf("attributes")) as? JsonObject) ?: continue
                val gradleUsage = (attrs["org.gradle.usage"] as? JsonPrimitive)?.content ?: continue

                if (gradleUsage != "java-runtime") continue
                val artifact = (v.resolvePath(listOf("available-at")) as? JsonObject) ?: continue
                val group = (artifact["group"] as? JsonPrimitive)?.content ?: continue
                val artifactId = (artifact["module"] as? JsonPrimitive)?.content ?: continue
                val version = (artifact["version"] as? JsonPrimitive)?.content ?: continue

                val artifactCoordinates = "$group:$artifactId:$version"
                coordinates.add(artifactCoordinates)
            }
        }
        jvmArtifactCallback(coordinates)
    }

    private fun makeResolutionResult(
        classpath: List<File>,
        scriptDiagnostics: List<ScriptDiagnostic>,
    ): ResultWithDiagnostics<List<File>> {
        return if (scriptDiagnostics.isEmpty()) classpath.asSuccess()
        else makeFailureResult(scriptDiagnostics)
    }

    private class Repo(
        val repo: MavenRepositoryCoordinates,
        val options: Options = Options.Empty,
    )

    companion object {
        private val CENTRAL_REPO_COORDINATES = MavenRepositoryCoordinates("https://repo1.maven.org/maven2/")
        private val CENTRAL_REPO = Repo(CENTRAL_REPO_COORDINATES)
    }
}
