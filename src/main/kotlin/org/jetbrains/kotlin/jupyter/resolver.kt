package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.mainKts.impl.IvyResolver
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.*

open class JupyterScriptDependenciesResolver(resolverConfig: ResolverConfig?) {

    private val resolver: ExternalDependenciesResolver

    init {
        resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), IvyResolver())
        resolverConfig?.repositories?.forEach { resolver.tryAddRepository(it) }
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
                    if (!resolver.tryAddRepository(annotation.value))
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                }
                is DependsOn -> {
                    val result = runBlocking { resolver.resolve(annotation.value) }
                    when (result) {
                        is ResultWithDiagnostics.Failure -> scriptDiagnostics.add(ScriptDiagnostic("Failed to resolve dependencies:\n" + result.reports.joinToString("\n") { it.message }))
                        is ResultWithDiagnostics.Success -> {
                            addedClasspath.addAll(result.value)
                            classpath.addAll(result.value)
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

