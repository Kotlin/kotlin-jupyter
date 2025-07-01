package org.jetbrains.kotlinx.jupyter.dependencies

import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.jvm.withUpdatedClasspath

open class ScriptDependencyAnnotationHandlerImpl(
    private val resolver: JupyterScriptDependenciesResolver,
) : ScriptDependencyAnnotationHandler {
    override fun configure(
        configuration: ScriptCompilationConfiguration,
        annotations: List<Annotation>,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        if (annotations.isEmpty()) return configuration.asSuccess()
        val scriptContents =
            object : ScriptContents {
                override val annotations: Iterable<Annotation> = annotations
                override val file: File? = null
                override val text: CharSequence? = null
            }
        return resolver
            .resolveFromAnnotations(scriptContents)
            .onSuccess { classpath ->
                onResolvedClasspath(configuration, classpath).asSuccess()
            }
    }

    protected open fun onResolvedClasspath(
        configuration: ScriptCompilationConfiguration,
        classpath: List<File>,
    ): ScriptCompilationConfiguration =
        configuration
            .let { if (classpath.isEmpty()) it else it.withUpdatedClasspath(classpath) }
}
