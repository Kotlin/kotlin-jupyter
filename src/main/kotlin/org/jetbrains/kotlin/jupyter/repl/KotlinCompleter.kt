package org.jetbrains.kotlin.jupyter.repl

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.jupyter.CompleteReply
import org.jetbrains.kotlin.jupyter.ErrorReply
import org.jetbrains.kotlin.jupyter.ListErrorsReply
import org.jetbrains.kotlin.jupyter.MessageContent
import org.jetbrains.kotlin.jupyter.Paragraph
import org.jetbrains.kotlin.jupyter.toSourceCodePositionWithNewAbsolute
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.api.valueOrNull

data class CompletionTokenBounds(val start: Int, val end: Int)

abstract class CompletionResult {
    abstract val message: MessageContent

    open class Success(
        private val matches: List<String>,
        private val bounds: CompletionTokenBounds,
        private val metadata: List<SourceCodeCompletionVariant>,
        private val text: String,
        private val cursor: Int
    ) : CompletionResult() {
        init {
            assert(matches.size == metadata.size)
        }

        override val message: MessageContent
            get() = CompleteReply(
                matches,
                bounds.start,
                bounds.end,
                Paragraph(cursor, text),
                Json.encodeToJsonElement(
                    mapOf(
                        "_jupyter_types_experimental" to metadata.map {
                            mapOf(
                                "text" to it.text,
                                "type" to it.tail,
                                "start" to bounds.start,
                                "end" to bounds.end
                            )
                        },
                        "_jupyter_extended_metadata" to metadata.map {
                            mapOf(
                                "text" to it.text,
                                "displayText" to it.displayText,
                                "icon" to it.icon,
                                "tail" to it.tail
                            )
                        }
                    )
                ).jsonObject,

            )

        @TestOnly
        fun sortedMatches(): List<String> = matches.sorted()
    }

    class Empty(
        text: String,
        cursor: Int
    ) : Success(emptyList(), CompletionTokenBounds(cursor, cursor), emptyList(), text, cursor)

    class Error(
        private val errorName: String,
        private val errorValue: String,
        private val traceBack: List<String>
    ) : CompletionResult() {
        override val message: MessageContent
            get() = ErrorReply(errorName, errorValue, traceBack)
    }
}

data class ListErrorsResult(val code: String, val errors: Sequence<ScriptDiagnostic> = emptySequence()) {
    val message: ListErrorsReply
        get() = ListErrorsReply(code, errors.toList())
}

internal class SourceCodeImpl(number: Int, override val text: String) : SourceCode {
    override val name: String = "Line_$number"
    override val locationId: String = "location_$number"
}

class KotlinCompleter {
    fun complete(compiler: ReplCompleter, configuration: ScriptCompilationConfiguration, code: String, preprocessedCode: String, id: Int, cursor: Int): CompletionResult {
        return try {
            val codeLine = SourceCodeImpl(id, code)
            val preprocessedCodeLine = SourceCodeImpl(id, preprocessedCode)
            val codePos = cursor.toSourceCodePositionWithNewAbsolute(codeLine, preprocessedCodeLine)
            val completionResult = codePos?.let { runBlocking { compiler.complete(preprocessedCodeLine, codePos, configuration) } }

            completionResult?.valueOrNull()?.toList()?.let { completionList ->
                getResult(code, cursor, completionList)
            } ?: CompletionResult.Empty(code, cursor)
        } catch (e: Exception) {
            CompletionResult.Error(e.javaClass.simpleName, e.message ?: "", e.stackTrace.map { it.toString() })
        }
    }

    companion object {
        fun getResult(code: String, cursor: Int, completions: List<SourceCodeCompletionVariant>): CompletionResult.Success {
            val bounds = getTokenBounds(code, cursor)
            return CompletionResult.Success(completions.map { it.text }, bounds, completions, code, cursor)
        }

        private fun getTokenBounds(buf: String, cursor: Int): CompletionTokenBounds {
            require(cursor <= buf.length) { "Position $cursor does not exist in code snippet <$buf>" }

            val startSubstring = buf.substring(0, cursor)

            val filter = { c: Char -> !c.isLetterOrDigit() && c != '_' }

            val start = startSubstring.indexOfLast(filter) + 1

            return CompletionTokenBounds(start, cursor)
        }
    }
}
