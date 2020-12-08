package org.jetbrains.kotlin.jupyter.repl

import org.jetbrains.kotlin.jupyter.ListErrorsReply
import kotlin.script.experimental.api.ScriptDiagnostic

data class ListErrorsResult(val code: String, val errors: Sequence<ScriptDiagnostic> = emptySequence()) {
    val message: ListErrorsReply
        get() = ListErrorsReply(code, errors.toList())
}
