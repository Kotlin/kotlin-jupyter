package org.jetbrains.kotlinx.jupyter.dependencies

import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.jvm.withUpdatedClasspath

open class MavenDepsOnAnnotationsConfigurator(private val resolver: JupyterScriptDependenciesResolver) {
    fun configure(configuration: ScriptCompilationConfiguration, annotations: List<Annotation>, script: SourceCode): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        if (annotations.isEmpty()) return configuration.asSuccess()
        val scriptContents = object : ScriptContents {
            override val annotations: Iterable<Annotation> = annotations
            override val file: File? = null
            override val text: CharSequence? = null
        }
        return try {
            resolver.resolveFromAnnotations(scriptContents)
                .onSuccess { classpath ->
                    onResolvedClasspath(configuration, classpath).asSuccess()
                }
        } catch (e: Throwable) {
            ResultWithDiagnostics.Failure(e.asDiagnostics(path = script.locationId))
        }
    }

    protected open fun onResolvedClasspath(
        configuration: ScriptCompilationConfiguration,
        classpath: List<File>
    ): ScriptCompilationConfiguration {
        return configuration
            .let { if (classpath.isEmpty()) it else it.withUpdatedClasspath(classpath) }
    }
}
