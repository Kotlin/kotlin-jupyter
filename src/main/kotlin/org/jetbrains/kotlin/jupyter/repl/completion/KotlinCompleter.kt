package org.jetbrains.kotlin.jupyter.repl.completion

import com.beust.klaxon.JsonObject
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.jupyter.jsonObject
import org.jetbrains.kotlin.cli.common.repl.IReplStageState
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompilerWithCompletion
import org.jetbrains.kotlin.utils.CompletionVariant
import org.jetbrains.kotlin.utils.KotlinReplError
import java.io.PrintWriter
import java.io.StringWriter

enum class CompletionStatus(private val value: String) {
    OK("ok"),
    ERROR("error");

    override fun toString(): String {
        return value
    }
}

data class CompletionTokenBounds(val start: Int, val end: Int)

abstract class CompletionResult(
        private val status: CompletionStatus
) {
    open fun toJson(): JsonObject {
        return jsonObject("status" to status.toString())
    }

    open class Success(
            private val matches: List<String>,
            private val bounds: CompletionTokenBounds,
            private val metadata: List<CompletionVariant>,
            private val text: String,
            private val cursor: Int
    ): CompletionResult(CompletionStatus.OK) {
        init {
            assert(matches.size == metadata.size)
        }

        override fun toJson(): JsonObject {
            val res = super.toJson()
            res["matches"] = matches
            res["cursor_start"] = bounds.start
            res["cursor_end"] = bounds.end
            res["metadata"] = mapOf(
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
            res["paragraph"] = mapOf(
                    "cursor" to cursor,
                    "text" to text
            )
            return res
        }

        @TestOnly
        fun sortedMatches(): List<String> = matches.sorted()
    }

    class Empty(
            text: String, cursor: Int
    ): CompletionResult.Success(emptyList(), CompletionTokenBounds(cursor, cursor), emptyList(), text, cursor)

    class Error(
            private val errorName: String,
            private val errorValue: String,
            private val traceBack: String
    ): CompletionResult(CompletionStatus.ERROR) {
        override fun toJson(): JsonObject {
            val res = super.toJson()
            res["ename"] = errorName
            res["evalue"] = errorValue
            res["traceback"] = traceBack
            return res
        }
    }
}

data class ErrorsListResponse(val code: String, val errors: List<KotlinReplError> = emptyList()) {
    fun toJson(): JsonObject {
        return jsonObject("code" to code,
                "errors" to errors.map {
            val er = jsonObject(
                    "message" to it.message,
                    "severity" to it.severity.name
            )

            val loc = it.loc
            if (loc != null) {
                val start = loc.start
                val end = loc.end
                er["start"] = jsonObject("line" to start.line, "col" to start.col)
                if (end != null)
                    er["end"] = jsonObject("line" to end.line, "col" to end.col)
            }
            er
        })
    }
}

class KotlinCompleter {
    fun complete(compiler: ReplCompilerWithCompletion, compilerState: IReplStageState<*>, code: String, id: Int, cursor: Int): CompletionResult {
        return try {
            val codeLine = ReplCodeLine(id, 0, code)
            val completionList = compiler.complete(compilerState, codeLine, cursor)

            val bounds = getTokenBounds(code, cursor)

            CompletionResult.Success(completionList.map { it.text }, bounds, completionList, code, cursor)
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            CompletionResult.Error(e.javaClass.simpleName, e.message ?: "", sw.toString())
        }
    }

    companion object {
        fun getTokenBounds(buf: String, cursor: Int): CompletionTokenBounds {
            require(cursor <= buf.length) { "Position $cursor does not exist in code snippet <$buf>" }

            val startSubstring = buf.substring(0, cursor)
            val endSubstring = buf.substring(cursor)

            val filter = {c: Char -> !c.isLetterOrDigit() && c != '_'}

            val start = startSubstring.indexOfLast(filter) + 1
            var end = endSubstring.indexOfFirst(filter)
            end = if (end == -1) {
                buf.length
            } else {
                end + startSubstring.length
            }

            return CompletionTokenBounds(start, end)

        }
    }
}
