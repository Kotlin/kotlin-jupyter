package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.ListErrorsReply
import kotlin.script.experimental.api.ScriptDiagnostic

data class ListErrorsResult(val code: String, val errors: Sequence<ScriptDiagnostic> = emptySequence()) {
    val message: ListErrorsReply
        get() = ListErrorsReply(code, errors.toList())
}
