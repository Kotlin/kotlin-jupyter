package org.jetbrains.kotlin.jupyter.config

import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.RefineConfigurationBuilder
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

fun getCompilationConfiguration(
    scriptClasspath: List<File> = emptyList(),
    scriptReceivers: List<Any> = emptyList(),
    jvmTargetVersion: String = "1.8",
    refinementAction: RefineConfigurationBuilder.() -> Unit = {}
): ScriptCompilationConfiguration {
    return ScriptCompilationConfiguration {
        hostConfiguration.update { it.withDefaultsFrom(defaultJvmScriptingHostConfiguration) }
        baseClass.put(KotlinType(ScriptTemplateWithDisplayHelpers::class))
        fileExtension.put("jupyter.kts")

        val classImports = listOf(
            DependsOn::class,
            Repository::class,
            ScriptTemplateWithDisplayHelpers::class,
        ).map { it.java.name }
        defaultImports(classImports + defaultGlobalImports)

        jvm {
            updateClasspath(scriptClasspath)
        }
        refineConfiguration(refinementAction)

        val receiversTypes = scriptReceivers.map { KotlinType(it.javaClass.canonicalName) }
        implicitReceivers(receiversTypes)
        skipExtensionsResolutionForImplicitsExceptInnermost(receiversTypes)

        compilerOptions(
            "-jvm-target",
            jvmTargetVersion,
            "-no-stdlib"
        )
    }
}
