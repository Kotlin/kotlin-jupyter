package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.compiler.util.actualClassLoader
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.removeDuplicates
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.lastSnippetClassLoader
import kotlin.script.experimental.util.LinkedSnippet

/**
 * Helper class for managing ScriptEvaluationConfiguration during script evaluation.
 * Handles the creation of evaluation configurations with proper classloader setup,
 * and tracks evaluated classes and their classloaders.
 */
class JupyterScriptEvaluationHelper(
    private val basicEvaluationConfiguration: ScriptEvaluationConfiguration,
) {
    private val classes = mutableListOf<KClass<*>>()

    private val baseClassLoader: ClassLoader
        get() = basicEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]!!

    val lastKClass: KClass<*>
        get() = classes.last()

    val lastClassLoader: ClassLoader
        get() = classes.lastOrNull()?.java?.classLoader ?: baseClassLoader

    /**
     * Creates an evaluation configuration for the given compiled script.
     * Sets up the classloader hierarchy properly and registers the evaluated class.
     *
     * @param compiledSnippet The compiled script snippet
     * @return The evaluation configuration to use for evaluating this script
     */
    fun createEvaluationConfiguration(
        snippet: SourceCode,
        compiledSnippet: LinkedSnippet<KJvmCompiledScript>,
    ): ScriptEvaluationConfiguration {
        val compiledScript = compiledSnippet.get()

        val configWithClassloader =
            basicEvaluationConfiguration.with {
                jvm {
                    lastSnippetClassLoader(lastClassLoader)
                    baseClassLoader(this@JupyterScriptEvaluationHelper.baseClassLoader.parent)
                }
            }

        val classLoader = compiledScript.getOrCreateActualClassloader(configWithClassloader)

        val evalConfig =
            configWithClassloader.with {
                jvm {
                    actualClassLoader(classLoader)
                }
            }

        when (val kClassWithDiagnostics = runBlocking { compiledScript.getClass(evalConfig) }) {
            is ResultWithDiagnostics.Failure -> {
                // Work-around for KT-74685
                val updatedDiagnostics = kClassWithDiagnostics.removeDuplicates()
                throw ReplCompilerException(snippet.text, updatedDiagnostics)
            }
            is ResultWithDiagnostics.Success -> {
                classes.add(kClassWithDiagnostics.value)
                return evalConfig
            }
        }
    }
}
