package org.jetbrains.kotlinx.jupyter.repl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.util.toSourceCodePositionWithNewAbsolute
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.api.valueOrNull

class KotlinCompleter {
    fun complete(
        compiler: ReplCompleter,
        configuration: ScriptCompilationConfiguration,
        code: String,
        preprocessedCode: String,
        id: Int,
        cursor: Int,
    ): CompletionResult {
        return try {
            val codeLine = SourceCodeImpl(id, code)
            val preprocessedCodeLine = SourceCodeImpl(id, preprocessedCode)
            val codePos = cursor.toSourceCodePositionWithNewAbsolute(codeLine, preprocessedCodeLine)
            val completionResult =
                codePos?.let { runBlocking { compiler.complete(preprocessedCodeLine, codePos, configuration) } }

            completionResult?.valueOrNull()?.toList()?.let { completionList ->
                getResult(code, cursor, completionList)
            } ?: CompletionResult.Empty(code, cursor)
        } catch (e: Exception) {
            CompletionResult.Error(e.javaClass.simpleName, e.message ?: "", e.stackTrace.map { it.toString() })
        }
    }

    companion object {
        fun getResult(
            code: String,
            cursor: Int,
            completions: List<SourceCodeCompletionVariant>,
        ): CompletionResult.Success {
            val bounds = getTokenBounds(code, cursor)
            return CompletionResult.Success(completions.map { it.text }, bounds, completions, code, cursor)
        }

        private fun getTokenBounds(buf: String, cursor: Int): CodeInterval {
            require(cursor <= buf.length) { "Position $cursor does not exist in code snippet <$buf>" }

            val startSubstring = buf.substring(0, cursor)

            val filter = { c: Char -> !c.isLetterOrDigit() && c != '_' }

            val start = startSubstring.indexOfLast(filter) + 1

            return CodeInterval(start, cursor)
        }
    }
}
