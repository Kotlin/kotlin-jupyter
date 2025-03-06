package org.jetbrains.kotlinx.jupyter.config

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
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

@JvmInline
value class CellId(val value: Int) {
    fun toExecutionCount(): ExecutionCount = ExecutionCount(value + 1)

    override fun toString(): String = value.toString()

    companion object {
        fun fromExecutionCount(count: ExecutionCount): CellId {
            return CellId(count.value - 1)
        }

        // Marker CellId for code executed outside the context of a cell.
        val NO_CELL: CellId = CellId(-1)
    }
}

data class JupyterCompilingOptions(
    val cellId: CellId,
    val isUserCode: Boolean,
) {
    companion object {
        val DEFAULT = JupyterCompilingOptions(CellId.NO_CELL, false)
    }
}

val ScriptCompilationConfigurationKeys.jupyterOptions by PropertiesCollection.key(
    isTransient = true,
    defaultValue = JupyterCompilingOptions.DEFAULT,
)

// Also called from IDEA
fun getCompilationConfiguration(
    scriptClasspath: List<File> = emptyList(),
    scriptReceivers: List<Any> = emptyList(),
    compilerArgsConfigurator: CompilerArgsConfigurator,
    scriptingClassGetter: GetScriptingClassByClassLoader = JvmGetScriptingClass(),
    scriptDataCollectors: List<ScriptDataCollector> = emptyList(),
    replCompilerMode: ReplCompilerMode = ReplCompilerMode.DEFAULT,
    loggerFactory: KernelLoggerFactory,
    body: ScriptCompilationConfiguration.Builder.() -> Unit = {},
): ScriptCompilationConfiguration {
    if (replCompilerMode == ReplCompilerMode.K2) {
        loggerFactory.getLogger("getCompilationConfiguration").warn("K2 Repl Mode is ignored for now. Falling back to K1")
    }
    return ScriptCompilationConfiguration {
        hostConfiguration.update {
            it.with {
                getScriptingClass(scriptingClassGetter)
            }
        }
        fileExtension.put("jupyter.kts")

        val classImports =
            listOf(
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
