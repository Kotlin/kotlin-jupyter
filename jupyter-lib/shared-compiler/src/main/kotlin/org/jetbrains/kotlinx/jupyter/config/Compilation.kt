package org.jetbrains.kotlinx.jupyter.config

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.configuration.configureDefaultRepl
import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.repl
import kotlin.script.experimental.api.resultFieldPrefix
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.util.PropertiesCollection

@JvmInline
value class CellId(
    val value: Int,
) {
    fun toExecutionCount(): ExecutionCount = ExecutionCount(value + 1)

    override fun toString(): String = value.toString()

    companion object {
        fun fromExecutionCount(count: ExecutionCount): CellId = CellId(count.value - 1)

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
    @Suppress("unused") loggerFactory: KernelLoggerFactory,
    body: ScriptCompilationConfiguration.Builder.() -> Unit = {},
): ScriptCompilationConfiguration =
    ScriptCompilationConfiguration {
        hostConfiguration.update {
            it.with {
                getScriptingClass(scriptingClassGetter)
                if (replCompilerMode == K2) {
                    configureDefaultRepl("jupyter.kts")
                }
            }
        }
        repl {
            resultFieldPrefix("\$res")
            // In K2, we need this because the snippet number tracked internally
            // in the compiler is not propagating correct to FirExtension
            // Without it, we cannot read return values correctly which makes TypeConverts fails.
            // In K1 it overrides, the $resX value which fails a lot of tests
            // Hopefully this will be fixed by https://youtrack.jetbrains.com/issue/KT-76172/K2-Repl-Snippet-classes-do-not-store-result-values
            // currentLineId(LineId(0, 0, 0))
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
                config
                    .with {
                        compilerOptions(compilerArgsConfigurator.getArgs())
                    }.asSuccess()
            }
        }

        ide {
            acceptedLocations(ScriptAcceptedLocation.Everywhere)
        }

        body()
    }

// Do not rename this method for backwards compatibility with IDEA.
inline fun <reified T> ScriptCompilationConfiguration.Builder.addBaseClass() {
    val kClass = T::class
    defaultImports.append(kClass.java.name)
    // In Kotlin 1.9, the classloader used was chosen by `ScriptDefinition.contextClassLoader`,
    // which used the one from `baseClass`, but in the K2 Repl, base classes are no longer
    // supported. Instead, APIs are injected through an implicit receiver. But this also broke
    // using the correct class loader.
    //
    // `ScriptDefinition` falls back to `ScriptingHostConfiguration[jvm.baseClassLoader]`, so
    // for now, we just override the setting here. But it is unclear if this is safe in all
    // use cases.
    //
    // This seems to mostly impact Embedded Mode inside IDEA. Unit tests did not catch the
    // problem.
    hostConfiguration.update {
        it.with {
            this[jvm.baseClassLoader] = kClass.java.classLoader
        }
    }
}
