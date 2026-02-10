package org.jetbrains.kotlinx.jupyter.repl

import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant

fun interface CompleteFunction {
    suspend fun complete(
        code: String,
        position: SourceCode.Position,
        snippetId: Int,
    ): List<SourceCodeCompletionVariant>
}
