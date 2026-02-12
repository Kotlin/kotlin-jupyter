package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import java.io.File
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

/**
 * This interface is the main interface for exposing the Kotlin REPL compiler inside the Kotlin Kernel.
 * It is a subcomponent of [org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter]
 *
 * @see ReplCompiler
 */
internal interface JupyterCompiler {
    val version: KotlinKernelVersion
    val numberOfSnippets: Int
    val previousScriptsClasses: List<KClass<*>>
    val lastKClass: KClass<*>
    val lastClassLoader: ClassLoader

    /**
     * Increments and return the value of the next execution count.
     * This value is used to uniquely identify each snippet handled by the compiler.
     */
    fun nextCounter(): Int

    fun addClasspathEntries(classpathEntries: List<File>)

    fun compileSync(
        snippet: SourceCode,
        options: JupyterCompilingOptions,
    ): Result

    data class Result(
        val snippet: LinkedSnippet<KJvmCompiledScript>,
        val newEvaluationConfiguration: ScriptEvaluationConfiguration,
    )
}
