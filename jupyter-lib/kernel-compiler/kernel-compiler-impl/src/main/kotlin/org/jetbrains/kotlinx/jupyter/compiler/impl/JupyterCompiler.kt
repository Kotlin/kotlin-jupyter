package org.jetbrains.kotlinx.jupyter.compiler.impl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

/**
 * This interface is a subcomponent of [org.jetbrains.kotlinx.jupyter.compiler.impl.CompilerServiceImpl].
 *
 * @see kotlin.script.experimental.api.ReplCompiler
 */
interface JupyterCompiler {
    val version: KotlinKernelVersion

    fun compileSync(
        snippetId: Int,
        code: String,
        options: JupyterCompilingOptions,
    ): LinkedSnippet<KJvmCompiledScript>

    fun complete(
        code: String,
        position: SourceCode.Position,
        snippetId: Int,
    ): List<SourceCodeCompletionVariant>

    fun checkComplete(
        code: Code,
        snippetId: Int,
    ): CheckCompletenessResult

    fun listErrors(
        code: Code,
        snippetId: Int,
    ): Sequence<ScriptDiagnostic>
}
