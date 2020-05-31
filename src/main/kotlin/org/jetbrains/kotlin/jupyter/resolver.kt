package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

open class JupyterScriptDependenciesResolver(resolverConfig: ResolverConfig?) {

    private val log by lazy { LoggerFactory.getLogger("resolver") }

    private val resolver: ExternalDependenciesResolver

    init {
        resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), MavenDependenciesResolver())
        resolverConfig?.repositories?.forEach { resolver.addRepository(it) }
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
                    log.info("Adding repository: ${annotation.value}")
                    if (resolver.addRepository(RepositoryCoordinates(annotation.value)).valueOrNull() != true)
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                }
                is DependsOn -> {
                    log.info("Resolving ${annotation.value}")
                    try {
                        val result = runBlocking { resolver.resolve(annotation.value) }
                        when (result) {
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
                }
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        return if (scriptDiagnostics.isEmpty()) classpath.asSuccess()
        else makeFailureResult(scriptDiagnostics)
    }
}

