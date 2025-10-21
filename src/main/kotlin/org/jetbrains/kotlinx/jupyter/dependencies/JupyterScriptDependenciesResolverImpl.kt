package org.jetbrains.kotlinx.jupyter.dependencies

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.dependencies.DependencyDescription
import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import org.jetbrains.kotlinx.jupyter.api.dependencies.ResolutionResult
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import java.io.File
import kotlin.reflect.KMutableProperty0
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.dependencies.ArtifactWithLocation

open class JupyterScriptDependenciesResolverImpl(
    loggerFactory: KernelLoggerFactory,
    mavenRepositories: List<MavenRepositoryCoordinates>,
    resolveSourcesOption: KMutableProperty0<Boolean>,
    resolveMppOption: KMutableProperty0<Boolean>,
    private val addedBinaryClasspath: MutableList<File>,
    private val addedSourceClasspath: MutableList<File>,
) : JupyterScriptDependenciesResolver {
    private val logger = loggerFactory.getLogger(JupyterScriptDependenciesResolverImpl::class.java)

    private val resolver: SourceAwareDependenciesResolver =
        CompoundSourceAwareDependenciesResolver(
            FileSystemSourceAwareDependenciesResolver(),
            AmperMavenDependenciesResolver(),
        )

    private val repositories = arrayListOf<RepositoryDescription>()

    override var resolveSources: Boolean by resolveSourcesOption
    override var resolveMpp: Boolean by resolveMppOption

    init {
        mavenRepositories.forEach { addRepository(RepositoryDescription(it.coordinates)) }
        // Ensure Central is present before the first resolution
        addRepository(CENTRAL_REPO)
    }

    private fun addRepository(repo: RepositoryDescription): Boolean {
        val repoIndex = repositories.indexOfFirst { it.value == repo.value }
        if (repoIndex != -1) repositories.removeAt(repoIndex)
        repositories.add(repo)
        return resolver.addRepository(repo, null).valueOrNull() == true
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
        for (repository in repositories) {
            logger.info("Adding repository: ${repository.value}")
            if (!addRepository(repository)) {
                throw IllegalArgumentException("Illegal argument for Repository annotation: $repository")
            }
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

    private suspend fun resolveWithOptions(dependencies: List<Dependency>): ResultWithDiagnostics<ResolvedArtifacts> {
        val artifacts = dependencies.map { ArtifactWithLocation(it.value, null) }
        val resolveSourcesFlag = dependencies.shouldResolveSources(resolveSources)
        return resolver.resolve(artifacts, resolveSourcesFlag)
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
            { resolveWithOptions(dependencies) },
            onBinaryResolved = onBinaryResolved,
            onSourceResolved = onSourceResolved,
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
    }

    private fun doResolve(
        resolveAction: suspend () -> ResultWithDiagnostics<ResolvedArtifacts>,
        onBinaryResolved: (List<File>) -> Unit,
        onSourceResolved: (List<File>) -> Unit,
        onFailure: (ResultWithDiagnostics.Failure) -> Unit,
    ) {
        when (val result = runBlocking { resolveAction() }) {
            is ResultWithDiagnostics.Failure -> {
                onFailure(result)
            }
            is ResultWithDiagnostics.Success -> {
                val resolvedArtifacts = result.value
                logger.info("Resolved binaries: " + resolvedArtifacts.binaries.joinToString())
                logger.info("Resolved sources: " + resolvedArtifacts.sources.joinToString())
                onBinaryResolved(resolvedArtifacts.binaries)
                onSourceResolved(resolvedArtifacts.sources)
            }
        }
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

    companion object {
        private val CENTRAL_REPO = RepositoryDescription("https://repo1.maven.org/maven2/")
    }
}
