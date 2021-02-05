package org.jetbrains.kotlinx.jupyter.codegen

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinKernelHostProvider
import jupyter.kotlin.Repository
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationCallback
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.dependencies.ScriptDependencyAnnotationHandler
import org.jetbrains.kotlinx.jupyter.repl.impl.JupyterCompiler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.foundAnnotations
import kotlin.script.experimental.api.valueOr

class FileAnnotationsProcessorImpl(
    val dependencyAnnotationsHandler: ScriptDependencyAnnotationHandler,
    val compilerArgsConfigurator: CompilerArgsConfigurator,
    val compiler: JupyterCompiler,
    val kernelHostProvider: KotlinKernelHostProvider
) : FileAnnotationsProcessor {

    private val handlers = mutableMapOf<String, FileAnnotationCallback>()

    override fun register(handler: FileAnnotationHandler) {
        handlers[handler.annotation.qualifiedName!!] = handler.callback
        compiler.updateCompilationConfigOnAnnotation(handler) { context ->
            process(context, kernelHostProvider.host!!)
        }
    }

    override fun process(
        context: ScriptConfigurationRefinementContext,
        host: KotlinKernelHost
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val collected = mutableListOf<Annotation>()

        fun processCollected(conf: ScriptCompilationConfiguration): ResultWithDiagnostics<ScriptCompilationConfiguration> {
            if (collected.isEmpty()) return conf.asSuccess()
            val clazz = collected[0].annotationClass
            return try {
                when (clazz) {
                    DependsOn::class, Repository::class -> dependencyAnnotationsHandler.configure(
                        conf,
                        collected
                    )
                    CompilerArgs::class -> compilerArgsConfigurator.configure(conf, collected)
                    else -> {
                        handlers[clazz.qualifiedName!!]?.invoke(host, collected)
                        conf.asSuccess()
                    }
                }
            } catch (e: Throwable) {
                ResultWithDiagnostics.Failure(e.asDiagnostics(path = context.script.locationId))
            } finally {
                collected.clear()
            }
        }

        var config = context.compilationConfiguration

        val foundAnnotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)

        foundAnnotations?.forEach {
            if (collected.isEmpty() || collected[0].annotationClass == it.annotationClass) collected.add(it)
            else {
                config = processCollected(config).valueOr { return it }
                collected.add(it)
            }
        }
        return processCollected(config)
    }
}
