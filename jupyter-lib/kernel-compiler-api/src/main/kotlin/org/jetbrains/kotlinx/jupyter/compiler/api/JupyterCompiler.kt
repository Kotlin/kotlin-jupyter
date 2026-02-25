package org.jetbrains.kotlinx.jupyter.compiler.api

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

/**
 * This interface is the main interface for exposing the Kotlin REPL compiler inside the Kotlin Kernel.
 * It is a subcomponent of [org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter]
 *
 * @see ReplCompiler
 */
interface JupyterCompiler {
    val version: KotlinKernelVersion

    /**
     * Increments and return the value of the next execution count.
     * This value is used to uniquely identify each snippet handled by the compiler.
     */
    fun nextCounter(): Int

    fun compileSync(
        snippet: SourceCode,
        options: JupyterCompilingOptions,
    ): LinkedSnippet<KJvmCompiledScript>
}
