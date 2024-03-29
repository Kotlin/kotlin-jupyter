package org.jetbrains.kotlinx.jupyter.dependencies

import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics

interface JupyterScriptDependenciesResolver {
    var resolveSources: Boolean
    var resolveMpp: Boolean

    fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>>

    fun popAddedClasspath(): List<File>

    fun popAddedSources(): List<File>
}
