package org.jetbrains.kotlinx.jupyter.compiler.api

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.repl.CheckCompletenessResult
import org.jetbrains.kotlinx.jupyter.repl.CompleteFunction
import kotlin.script.experimental.api.ScriptDiagnostic

interface JupyterCompilerWithCompletion : JupyterCompiler {
    val complete: CompleteFunction

    fun checkComplete(code: Code): CheckCompletenessResult

    fun listErrors(code: Code): Sequence<ScriptDiagnostic>
}
