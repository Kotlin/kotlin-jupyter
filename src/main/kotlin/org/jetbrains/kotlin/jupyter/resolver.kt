package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.*
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

class PresetDependenciesResolver(private val baseResolver: ExternalDependenciesResolver, repositories: Iterable<RepositoryCoordinates>, private val artifactsMapping: Map<String, String>)
    : ExternalDependenciesResolver by baseResolver {

    init {
        repositories.forEach { baseResolver.addRepository(it) }
    }

    override suspend fun resolve(artifactCoordinates: String): ResultWithDiagnostics<List<File>> =
            baseResolver.resolve(artifactsMapping[artifactCoordinates] ?: artifactCoordinates)

    override fun acceptsArtifact(artifactCoordinates: String) = artifactsMapping.containsKey(artifactCoordinates) || baseResolver.acceptsArtifact(artifactCoordinates)
}

open class JupyterScriptDependenciesResolver(val librariesConfig: LibrariesConfig?) {
    private val resolver: ExternalDependenciesResolver

    init {
        var mavenResolver = MavenDependenciesResolver() as ExternalDependenciesResolver
        if (librariesConfig != null)
            mavenResolver = PresetDependenciesResolver(mavenResolver, librariesConfig.repositories, librariesConfig.artifactsMapping.mapValues { it.value.coordinates })
        resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), mavenResolver)
    }

    private val addedImports = mutableListOf<String>()

    fun getNewImports(): List<String> {
        val result = addedImports.toList()
        addedImports.clear()
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
                            librariesConfig?.let {
                                it.artifactsMapping[annotation.value]?.let { addedImports.addAll(it.imports) }
                            }
                        }
                    }
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        return if(scriptDiagnostics.isEmpty()) classpath.asSuccess()
               else makeFailureResult(scriptDiagnostics)
    }
}

