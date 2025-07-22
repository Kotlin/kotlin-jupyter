package org.jetbrains.kotlinx.jupyter.dependencies

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.resolvePath
import java.io.File
import kotlin.reflect.KMutableProperty0
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

open class JupyterScriptDependenciesResolverImpl(
    loggerFactory: KernelLoggerFactory,
    mavenRepositories: List<MavenRepositoryCoordinates>,
    resolveSourcesOption: KMutableProperty0<Boolean>,
    resolveMppOption: KMutableProperty0<Boolean>,
) : JupyterScriptDependenciesResolver {
    private val logger = loggerFactory.getLogger(JupyterScriptDependenciesResolverImpl::class.java)

    private val resolver: ExternalDependenciesResolver =
        CompoundDependenciesResolver(
            FileSystemDependenciesResolver(),
            RemoteResolverWrapper(MavenDependenciesResolver(true)),
        )
    private val resolverOptions =
        buildOptions(
            DependenciesResolverOptionsName.SCOPE to "compile,runtime",
        )

    private val sourcesResolverOptions =
        buildOptions(
            DependenciesResolverOptionsName.PARTIAL_RESOLUTION to "true",
            DependenciesResolverOptionsName.SCOPE to "compile,runtime",
            DependenciesResolverOptionsName.CLASSIFIER to "sources",
            DependenciesResolverOptionsName.EXTENSION to "jar",
        )

    private val mppResolverOptions =
        buildOptions(
            DependenciesResolverOptionsName.PARTIAL_RESOLUTION to "true",
            DependenciesResolverOptionsName.SCOPE to "compile,runtime",
            DependenciesResolverOptionsName.EXTENSION to "module",
        )

    private val repositories = arrayListOf<Repo>()
    private val addedClasspath = arrayListOf<File>()

    override var resolveSources: Boolean by resolveSourcesOption
    override var resolveMpp: Boolean by resolveMppOption
    private val addedSourcesClasspath = arrayListOf<File>()

    init {
        mavenRepositories.forEach { addRepository(Repo(it)) }
    }

    private fun buildOptions(vararg options: Pair<DependenciesResolverOptionsName, String>): Options =
        makeExternalDependenciesResolverOptions(
            mutableMapOf<String, String>().apply {
                for (option in options) this[option.first] = option.second
            },
        )

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
        val dependencies = mutableListOf<Dependency>()

        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    logger.info("Adding repository: ${annotation.value}")
                    val newRepositories = existingRepositories ?: ArrayList(repositories)
                    existingRepositories = newRepositories

                    val options =
                        if (annotation.username.isNotEmpty() || annotation.password.isNotEmpty()) {
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

                    newRepositories.forEach { addRepository(it) }
                }
                is DependsOn -> {
                    dependencies.add(annotation.toDependency())
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }

        try {
            tryResolve(
                dependencies,
                scriptDiagnostics,
                classpath,
            )
        } catch (e: Exception) {
            val diagnostic = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unhandled exception during resolve", exception = e)
            logger.error(diagnostic.message, e)
            scriptDiagnostics.add(diagnostic)
        }
        // Hack: after first resolution add "standard" Central repo to the end of the list, giving it the lowest priority
        addRepository(CENTRAL_REPO)

        return makeResolutionResult(classpath, scriptDiagnostics)
    }

    private suspend fun resolveWithOptions(
        dependencies: List<Dependency>,
        options: Options,
    ): ResultWithDiagnostics<List<File>> = resolver.resolve(dependencies.map { ArtifactWithLocation(it.value, null) }, options)

    private fun tryResolve(
        dependencies: List<Dependency>,
        scriptDiagnosticsResult: MutableList<ScriptDiagnostic>,
        classpathResult: MutableList<File>,
    ) {
        if (dependencies.isEmpty()) return

        logger.info("Resolving $dependencies")
        doResolve(
            { resolveWithOptions(dependencies, resolverOptions) },
            onResolved = { files ->
                addedClasspath.addAll(files)
                classpathResult.addAll(files)
            },
            onFailure = { result ->
                val diagnostics =
                    ScriptDiagnostic(
                        ScriptDiagnostic.unspecifiedError,
                        "Failed to resolve $dependencies:\n" + result.reports.joinToString("\n") { it.message },
                    )
                logger.warn(diagnostics.message, diagnostics.exception)
                scriptDiagnosticsResult.add(diagnostics)
            },
        )

        if (dependencies.shouldResolveSources(resolveSources)) {
            doResolve(
                { resolveWithOptions(dependencies, sourcesResolverOptions) },
                onResolved = { files ->
                    addedSourcesClasspath.addAll(files)
                },
                onFailure = { result ->
                    logger.warn("Failed to resolve sources for $dependencies:\n" + result.reports.joinToString("\n") { it.message })
                },
            )
        }

        if (dependencies.shouldResolveAsMultiplatform(resolveMpp)) {
            doResolve(
                { resolveWithOptions(dependencies, mppResolverOptions) },
                onResolved = { files ->
                    val resolvedArtifacts = mutableSetOf<Dependency>()
                    resolvedArtifacts.addAll(dependencies)
                    resolveMpp(files) { artifactCoordinates ->
                        val artifacts = artifactCoordinates.map { it.toDependency() }
                        val notYetResolvedArtifacts =
                            artifacts.filter { artifact ->
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
                logger.info("Resolved: " + result.value.joinToString())
                onResolved(result.value)
            }
        }
    }

    private fun resolveMpp(
        moduleFiles: List<File>,
        jvmArtifactCallback: (List<String>) -> Unit,
    ) {
        val coordinates = mutableListOf<String>()

        for (moduleFile in moduleFiles) {
            val json =
                try {
                    Json.parseToJsonElement(moduleFile.readText())
                } catch (_: Throwable) {
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
    ): ResultWithDiagnostics<List<File>> =
        if (scriptDiagnostics.isEmpty()) {
            classpath.asSuccess()
        } else {
            makeFailureResult(scriptDiagnostics)
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
