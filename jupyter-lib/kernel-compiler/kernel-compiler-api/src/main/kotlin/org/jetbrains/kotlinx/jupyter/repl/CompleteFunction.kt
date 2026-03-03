package org.jetbrains.kotlinx.jupyter.repl

import kotlin.script.experimental.api.ReplCompletionResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode

fun interface CompleteFunction {
    suspend fun complete(
        code: String,
        position: SourceCode.Position,
        snippetId: Int,
    ): ResultWithDiagnostics<ReplCompletionResult>
}
