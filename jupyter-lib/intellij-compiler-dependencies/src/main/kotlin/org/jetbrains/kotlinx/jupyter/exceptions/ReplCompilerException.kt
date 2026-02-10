package org.jetbrains.kotlinx.jupyter.exceptions

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplExceptionCause
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

private fun enhanceReplCompilerError(
    metadata: CellErrorMetaData?,
    message: String,
): String {
    if (metadata == null || message.isEmpty()) return message
    // Possible patterns we need to look out for:
    // - Line_0.jupyter.kts (1:4 - 4) Some message
    val pattern = "Line_\\d+\\.jupyter\\.kts \\((?<line>\\d+):(?<column>\\d+) - \\d+\\) (?<message>.*)".toRegex()
    return message.lines().joinToString("\n") { line ->
        pattern.find(line)?.let { match ->
            val lineNumber: Int = match.groups["line"]!!.value.toInt()
            val columnNumber: Int = match.groups["column"]!!.value.toInt()
            val msg = match.groups["message"]!!.value
            // In this case, we also show the line number even if it is outside the visible
            // range, since hiding it would make debugging harder in case of bugs in compiler
            // plugins that modify the code.
            "at Cell In[${metadata.executionCount}], line $lineNumber, column $columnNumber: $msg"
        } ?: line
    }
}

/**
 * Exception type for compile time errors happening in the user's code.
 */
class ReplCompilerException(
    val failedCode: String,
    val errorResult: ResultWithDiagnostics.Failure? = null,
    message: String? = null,
    metadata: CellErrorMetaData? = null,
) : ReplException(
        enhanceReplCompilerError(metadata, message ?: errorResult?.getErrors() ?: ""),
        errorResult?.reports?.map { it.exception }?.firstOrNull(),
    ),
    ReplExceptionCause {
    val firstError: ScriptDiagnostic? =
        errorResult?.reports?.firstOrNull {
            it.severity == ScriptDiagnostic.Severity.ERROR || it.severity == ScriptDiagnostic.Severity.FATAL
        }

    override fun getAdditionalInfoJson(): JsonObject? =
        firstError?.location?.let {
            val errorMessage = firstError.message
            JsonObject(
                mapOf(
                    "lineStart" to JsonPrimitive(it.start.line),
                    "colStart" to JsonPrimitive(it.start.col),
                    "lineEnd" to JsonPrimitive(it.end?.line ?: -1),
                    "colEnd" to JsonPrimitive(it.end?.col ?: -1),
                    "message" to JsonPrimitive(errorMessage),
                    "path" to JsonPrimitive(firstError.sourcePath.orEmpty()),
                ),
            )
        }

    override fun render() = message

    override fun StringBuilder.appendCause() {
        appendLine("Error compiling code:")
        appendLine(failedCode)
        errorResult?.let { errors ->
            appendLine("\nErrors:")
            appendLine(errors.getErrors())
            appendLine()
        }
    }
}

fun <T> ResultWithDiagnostics<T>.getErrors(): String {
    val filteredReports =
        reports.filter {
            it.code != ScriptDiagnostic.incompleteCode
        }

    return filteredReports.joinToString("\n") { report ->
        report.location
            ?.let { loc ->
                report.sourcePath
                    ?.let { sourcePath ->
                        compilerDiagnosticToString(
                            sourcePath,
                            loc.start.line,
                            loc.start.col,
                            loc.end?.line ?: -1,
                            loc.end?.col ?: -1,
                        )
                    }?.let {
                        "$it "
                    }
            }.orEmpty() + report.message
    }
}

fun compilerDiagnosticToString(
    path: String,
    line: Int,
    column: Int,
    lineEnd: Int,
    columnEnd: Int,
): String {
    val start =
        if (line == -1 && column == -1) {
            ""
        } else {
            "$line:$column"
        }
    val end =
        if (lineEnd == -1 && columnEnd == -1) {
            ""
        } else if (lineEnd == line) {
            " - $columnEnd"
        } else {
            " - $lineEnd:$columnEnd"
        }
    val loc = if (start.isEmpty() && end.isEmpty()) "" else " ($start$end)"
    return path + loc
}
