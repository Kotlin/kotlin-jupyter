package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

class AliasDependenciesResolver(private val baseResolver: ExternalDependenciesResolver, private val artifactsMapping: Map<String, List<String>>)
    : ExternalDependenciesResolver by baseResolver {

    private operator fun <R> ResultWithDiagnostics<List<R>>.plus(result: ResultWithDiagnostics<List<R>>): ResultWithDiagnostics<List<R>> =
            this.onSuccess { l1 -> result.onSuccess { (l1 + it).asSuccess() } }

    private fun <T> ResultWithDiagnostics.Success<T>.asResult(): ResultWithDiagnostics<T> = this

    override suspend fun resolve(artifactCoordinates: String): ResultWithDiagnostics<List<File>> {
        val artifacts = artifactsMapping[artifactCoordinates]
        return artifacts?.map { baseResolver.resolve(it) }?.fold(emptyList<File>().asSuccess().asResult()) { p, n -> p + n }
                ?: baseResolver.resolve(artifactCoordinates)
    }

    override fun acceptsArtifact(artifactCoordinates: String) =
            artifactsMapping.containsKey(artifactCoordinates) || baseResolver.acceptsArtifact(artifactCoordinates)
}

open class JupyterScriptDependenciesResolver(val resolverConfig: ResolverConfig?) {
    private val resolver: ExternalDependenciesResolver

    init {
        var r: ExternalDependenciesResolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), MavenDependenciesResolver())
        if (resolverConfig != null) {
            if (resolverConfig.libraries.isNotEmpty()) r = AliasDependenciesResolver(r, resolverConfig.libraries.mapValues { it.value.artifacts })
            resolverConfig.repositories.forEach { r.tryAddRepository(it) }
        }
        resolver = r
    }

    private val newArtifacts = mutableListOf<ArtifactResolution>()

    fun getAdditionalInitializationCode(): List<String> {
        if (newArtifacts.isEmpty()) return emptyList()
        val importsCode = newArtifacts.joinToString("\n") { it.imports.joinToString("\n") { "import $it" } }
        val initCodes = newArtifacts.flatMap { it.initCodes }
        val result = listOf(importsCode) + initCodes
        newArtifacts.clear()
        return result
    }

    fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>> {
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()
        val classpath = mutableListOf<File>()

        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    if (!resolver.tryAddRepository(annotation.value))
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                }
                is DependsOn -> {
                    val result = runBlocking { resolver.resolve(annotation.value) }
                    when (result) {
                        is ResultWithDiagnostics.Failure -> scriptDiagnostics.add(ScriptDiagnostic("Failed to resolve dependencies:\n" + result.reports.joinToString("\n") { it.message }))
                        is ResultWithDiagnostics.Success -> {
                            classpath.addAll(result.value)
                            resolverConfig?.let {
                                it.libraries[annotation.value]?.let { newArtifacts.add(it) }
                            }
                        }
                    }
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        return if (scriptDiagnostics.isEmpty()) classpath.asSuccess()
        else makeFailureResult(scriptDiagnostics)
    }
}

