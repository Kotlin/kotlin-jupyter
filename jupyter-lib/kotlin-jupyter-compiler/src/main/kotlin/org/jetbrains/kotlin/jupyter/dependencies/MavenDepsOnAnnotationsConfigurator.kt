package org.jetbrains.kotlin.jupyter.dependencies

import java.io.File
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.foundAnnotations
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.jvm.withUpdatedClasspath

open class MavenDepsOnAnnotationsConfigurator(private val resolver: JupyterScriptDependenciesResolver) {
    fun configure(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()
        val scriptContents = object : ScriptContents {
            override val annotations: Iterable<Annotation> = annotations
            override val file: File? = null
            override val text: CharSequence? = null
        }
        return try {
            resolver.resolveFromAnnotations(scriptContents)
                .onSuccess { classpath ->
                    onResolvedClasspath(context, classpath).asSuccess()
                }
        } catch (e: Throwable) {
            ResultWithDiagnostics.Failure(e.asDiagnostics(path = context.script.locationId))
        }
    }

    protected open fun onResolvedClasspath(
        context: ScriptConfigurationRefinementContext,
        classpath: List<File>
    ): ScriptCompilationConfiguration {
        return context.compilationConfiguration
            .let { if (classpath.isEmpty()) it else it.withUpdatedClasspath(classpath) }
    }
}
