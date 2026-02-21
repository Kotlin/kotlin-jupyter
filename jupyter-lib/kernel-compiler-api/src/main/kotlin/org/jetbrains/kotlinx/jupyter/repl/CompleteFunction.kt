package org.jetbrains.kotlinx.jupyter.repl

import kotlin.script.experimental.api.ReplCompletionResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode

typealias CompleteFunction = suspend (SourceCode, SourceCode.Position) -> ResultWithDiagnostics<ReplCompletionResult>
