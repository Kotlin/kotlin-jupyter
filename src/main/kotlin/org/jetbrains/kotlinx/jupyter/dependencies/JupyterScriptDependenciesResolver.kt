package org.jetbrains.kotlinx.jupyter.dependencies

import org.jetbrains.kotlinx.jupyter.api.dependencies.DependencyResolver
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics

interface JupyterScriptDependenciesResolver : DependencyResolver {
    fun resolveFromAnnotations(annotations: List<Annotation>): ResultWithDiagnostics<List<File>>
}
