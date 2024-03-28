package org.jetbrains.kotlinx.jupyter.config

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompilationConfigurationKeys
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
import kotlin.script.experimental.util.PropertiesCollection

data class JupyterCompilingOptions(
    val cellId: Int,
    val isUserCode: Boolean,
) {
    companion object {
        val DEFAULT = JupyterCompilingOptions(-1, false)
    }
}

val ScriptCompilationConfigurationKeys.jupyterOptions by PropertiesCollection.key(isTransient = true, defaultValue = JupyterCompilingOptions.DEFAULT)

fun getCompilationConfiguration(
    scriptClasspath: List<File> = emptyList(),
    scriptReceivers: List<Any> = emptyList(),
    compilerArgsConfigurator: CompilerArgsConfigurator,
    scriptingClassGetter: GetScriptingClassByClassLoader = JvmGetScriptingClass(),
    scriptDataCollectors: List<ScriptDataCollector> = emptyList(),
    body: ScriptCompilationConfiguration.Builder.() -> Unit = {},
): ScriptCompilationConfiguration {
    return ScriptCompilationConfiguration {
        hostConfiguration.update {
            it.with {
                getScriptingClass(scriptingClassGetter)
            }
        }
        fileExtension.put("jupyter.kts")

        val classImports = listOf(
            DependsOn::class,
            Repository::class,
            CompilerArgs::class,
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
                val scriptInfo = ScriptDataCollector.ScriptInfo(source, config[jupyterOptions]!!.isUserCode)
                for (collector in scriptDataCollectors) {
                    collector.collect(scriptInfo)
                }
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

inline fun <reified T> ScriptCompilationConfiguration.Builder.addBaseClass() {
    val kClass = T::class
    defaultImports.append(kClass.java.name)
    baseClass.put(KotlinType(kClass))
}
