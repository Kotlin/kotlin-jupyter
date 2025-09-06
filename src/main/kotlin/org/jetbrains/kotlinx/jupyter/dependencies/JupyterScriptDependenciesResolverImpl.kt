package org.jetbrains.kotlinx.jupyter.dependencies

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.dependencies.DependencyDescription
import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import org.jetbrains.kotlinx.jupyter.api.dependencies.ResolutionResult
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.resolvePath
import java.io.File
import kotlin.reflect.KMutableProperty0
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
    private val addedBinaryClasspath: MutableList<File>,
    private val addedSourceClasspath: MutableList<File>,
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

    override var resolveSources: Boolean by resolveSourcesOption
    override var resolveMpp: Boolean by resolveMppOption

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

    override fun resolveFromAnnotations(annotations: List<Annotation>): ResultWithDiagnostics<List<File>> {
        if (annotations.isEmpty()) return ResultWithDiagnostics.Success(emptyList())

        val repositories: MutableList<RepositoryDescription> = mutableListOf()
        val dependencies: MutableList<Dependency> = mutableListOf()

        for (annotation in annotations) {
            when (annotation) {
                is Repository ->
                    repositories.add(
                        RepositoryDescription(
                            annotation.value,
                            annotation.username,
                            annotation.password,
                        ),
                    )
                is DependsOn ->
                    dependencies.add(
                        annotation.toDependency(),
                    )
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }

        addRepositories(repositories)

        val classpath = mutableListOf<File>()
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()

        doResolveSafe(scriptDiagnostics) {
            tryResolve(
                dependencies = dependencies,
                scriptDiagnosticsResult = scriptDiagnostics,
                onBinaryResolved = { files ->
                    addedBinaryClasspath.addAll(files)
                    classpath.addAll(files)
                },
                onSourceResolved = { files ->
                    addedSourceClasspath.addAll(files)
                },
            )
        }

        return makeResolutionResult(classpath, scriptDiagnostics)
    }

    override fun addRepositories(repositories: List<RepositoryDescription>) {
        if (repositories.isEmpty()) return

        var existingRepositories: List<Repo>? = null
        for (repository in repositories) {
            logger.info("Adding repository: ${repository.value}")
            val newRepositories = existingRepositories ?: ArrayList(this.repositories)
            existingRepositories = newRepositories

            val options =
                if (repository.username.isNotEmpty() || repository.password.isNotEmpty()) {
                    buildOptions(
                        DependenciesResolverOptionsName.USERNAME to repository.username,
                        DependenciesResolverOptionsName.PASSWORD to repository.password,
                    )
                } else {
                    Options.Empty
                }
            val repo = Repo(MavenRepositoryCoordinates(repository.value), options)

            if (!addRepository(repo)) {
                throw IllegalArgumentException("Illegal argument for Repository annotation: $repository")
            }

            newRepositories.forEach { addRepository(it) }
        }
    }

    override fun resolve(
        dependencyDescriptions: Collection<DependencyDescription>,
        addToClasspath: Boolean,
    ): ResolutionResult {
        val dependencies = dependencyDescriptions.map { it.description.toDependency() }
        val binaryClasspath = mutableListOf<File>()
        val sourceClasspath = mutableListOf<File>()
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()

        doResolveSafe(scriptDiagnostics) {
            tryResolve(
                dependencies = dependencies,
                scriptDiagnosticsResult = scriptDiagnostics,
                onBinaryResolved = { files ->
                    if (addToClasspath) {
                        addedBinaryClasspath.addAll(files)
                    }
                    binaryClasspath.addAll(files)
                },
                onSourceResolved = { files ->
                    if (addToClasspath) {
                        addedSourceClasspath.addAll(files)
                    }
                    sourceClasspath.addAll(files)
                },
            )
        }

        return if (scriptDiagnostics.isEmpty()) {
            ResolutionResult.Success(binaryClasspath, sourceClasspath)
        } else {
            val message =
                scriptDiagnostics.joinToString("\n") { diagnostic ->
                    diagnostic.render()
                }
            ResolutionResult.Failure(message)
        }
    }

    private fun doResolveSafe(
        diagnostics: MutableList<ScriptDiagnostic>,
        doResolve: () -> Unit,
    ) {
        try {
            doResolve()
        } catch (e: Exception) {
            val diagnostic = ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "Unhandled exception during resolve", exception = e)
            logger.error(diagnostic.message, e)
            diagnostics.add(diagnostic)
        }
        // Hack: after the first resolution add "standard" Central repo to the end of the list, giving it the lowest priority
        addRepository(CENTRAL_REPO)
    }

    private suspend fun resolveWithOptions(
        dependencies: List<Dependency>,
        options: Options,
    ): ResultWithDiagnostics<List<File>> {
        val artifacts =
            dependencies.map {
                ArtifactWithLocation(it.value, null)
            }
        return resolver.resolve(artifacts, options)
    }

    private fun tryResolve(
        dependencies: List<Dependency>,
        scriptDiagnosticsResult: MutableList<ScriptDiagnostic>,
        onBinaryResolved: (List<File>) -> Unit,
        onSourceResolved: (List<File>) -> Unit,
    ) {
        if (dependencies.isEmpty()) return

        logger.info("Resolving $dependencies")
        doResolve(
            { resolveWithOptions(dependencies, resolverOptions) },
            onResolved = onBinaryResolved,
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
                onResolved = onSourceResolved,
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
                            onBinaryResolved,
                            onSourceResolved,
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
