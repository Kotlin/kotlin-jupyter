package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import org.jetbrains.kotlin.script.util.resolvers.DirectResolver
import org.jetbrains.kotlin.script.util.resolvers.FlatLibDirectoryResolver
import org.jetbrains.kotlin.script.util.resolvers.MavenResolver
import org.jetbrains.kotlin.script.util.resolvers.experimental.*
import java.io.File
import kotlin.script.dependencies.ScriptContents

open class JupyterScriptDependenciesResolver
{
    private var flatLibDirectoryResolver : GenericResolver = FlatLibDirectoryResolver()

    private var directResolver : GenericResolver = DirectResolver()

    private var mavenResolver : GenericResolver = MavenResolver()

    private val DependsOn.coordinates : GenericArtifactCoordinates
            get() = MavenArtifactCoordinates(value, groupId, artifactId, version)

    private val Repository.coordinates : GenericRepositoryCoordinates
            get () = BasicRepositoryCoordinates(value.takeIf { it.isNotBlank() } ?: url, id.takeIf { it.isNotBlank() })


    private val resolvers = sequenceOf(flatLibDirectoryResolver, directResolver, mavenResolver)

    fun resolveFromAnnotations(script: ScriptContents): List<File> {
        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    val coordinates = annotation.coordinates
                    if (!flatLibDirectoryResolver.tryAddRepository(coordinates) && !mavenResolver.tryAddRepository(coordinates))
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                }
                is DependsOn -> {}
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        return script.annotations.filterIsInstance(DependsOn::class.java).flatMap { dep ->
            resolvers.mapNotNull { it.tryResolve(dep.coordinates) }.firstOrNull() ?:
            throw Exception("Unable to resolve dependency $dep")
        }
    }
}

