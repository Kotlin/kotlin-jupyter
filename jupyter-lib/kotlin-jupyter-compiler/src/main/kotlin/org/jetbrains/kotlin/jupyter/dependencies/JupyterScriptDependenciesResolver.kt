package org.jetbrains.kotlin.jupyter.dependencies

import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics

interface JupyterScriptDependenciesResolver {
    fun resolveFromAnnotations(script: ScriptContents): ResultWithDiagnostics<List<File>>
}
