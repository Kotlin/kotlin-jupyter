package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.*
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult

open class JupyterScriptDependenciesResolver
{
    private val resolver : GenericDependenciesResolver = CompoundDependenciesResolver(DirectDependenciesResolver(), FlatLibDirectoryDependenciesResolver(), MavenDependenciesResolver())

    fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>> {
        script.annotations.forEach { annotation ->
            when (annotation) {
                is Repository -> {
                    if (!resolver.tryAddRepository(annotation.value))
                        throw IllegalArgumentException("Illegal argument for Repository annotation: $annotation")
                }
                is DependsOn -> {}
                else -> throw Exception("Unknown annotation ${annotation.javaClass}")
            }
        }
        val scriptDiagnostics = mutableListOf<ScriptDiagnostic>()
        val classpath = mutableListOf<File>()
        for(annotation in script.annotations.filterIsInstance<DependsOn>()) {
            when (val result = resolver.resolve(annotation.value)) {
                is ResultWithDiagnostics.Failure -> scriptDiagnostics.add(ScriptDiagnostic("Failed to resolve dependencies:\n" + result.reports.joinToString("\n") { it.message }))
                is ResultWithDiagnostics.Success -> classpath.addAll(result.value)
            }
        }
        return if(scriptDiagnostics.isEmpty()) classpath.asSuccess()
               else makeFailureResult(scriptDiagnostics)
    }
}

