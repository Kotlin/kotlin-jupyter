package org.jetbrains.kotlinx.jupyter.config

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

fun getCompilationConfiguration(
    scriptClasspath: List<File> = emptyList(),
    scriptReceivers: List<Any> = emptyList(),
    compilerArgsConfigurator: CompilerArgsConfigurator,
    scriptingClassGetter: GetScriptingClassByClassLoader = JvmGetScriptingClass(),
    importsCollector: ScriptImportsCollector = ScriptImportsCollector.NoOp,
    body: ScriptCompilationConfiguration.Builder.() -> Unit = {},
): ScriptCompilationConfiguration {
    return ScriptCompilationConfiguration {
        hostConfiguration.update {
            it.with {
                getScriptingClass(scriptingClassGetter)
            }
        }
        baseClass.put(KotlinType(ScriptTemplateWithDisplayHelpers::class))
        fileExtension.put("jupyter.kts")

        val classImports = listOf(
            DependsOn::class,
            Repository::class,
            CompilerArgs::class,
            ScriptTemplateWithDisplayHelpers::class,
        ).map { it.java.name }
        defaultImports(classImports + defaultGlobalImports)

        jvm {
            updateClasspath(scriptClasspath)
        }

        val receiversTypes = scriptReceivers.map { KotlinType(it.javaClass.canonicalName) }
        implicitReceivers(receiversTypes)
        skipExtensionsResolutionForImplicitsExceptInnermost(receiversTypes)

        compilerOptions(compilerArgsConfigurator.getArgs())

        refineConfiguration {
            beforeCompiling { (source, config, _) ->
                importsCollector.collect(source)
                config.with {
                    compilerOptions(compilerArgsConfigurator.getArgs())
                }.asSuccess()
            }
        }

        ide {
            acceptedLocations(ScriptAcceptedLocation.Everywhere)
        }

        body()
    }
}
