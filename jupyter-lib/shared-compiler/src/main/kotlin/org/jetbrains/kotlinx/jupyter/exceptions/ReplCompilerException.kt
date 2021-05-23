package org.jetbrains.kotlinx.jupyter.exceptions

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

class ReplCompilerException(
    val failedCode: String,
    val errorResult: ResultWithDiagnostics.Failure? = null,
    message: String? = null
) :
    ReplException(
        message ?: errorResult?.getErrors() ?: "",
        errorResult?.reports?.map { it.exception }?.firstOrNull()
    ) {

    val firstError = errorResult?.reports?.firstOrNull {
        it.severity == ScriptDiagnostic.Severity.ERROR || it.severity == ScriptDiagnostic.Severity.FATAL
    }

    override fun getAdditionalInfoJson(): JsonObject? {
        return firstError?.location?.let {
            val errorMessage = firstError.message
            JsonObject(
                mapOf(
                    "lineStart" to JsonPrimitive(it.start.line),
                    "colStart" to JsonPrimitive(it.start.col),
                    "lineEnd" to JsonPrimitive(it.end?.line ?: -1),
                    "colEnd" to JsonPrimitive(it.end?.col ?: -1),
                    "message" to JsonPrimitive(errorMessage),
                    "path" to JsonPrimitive(firstError.sourcePath.orEmpty())
                )
            )
        }
    }

    override fun render() = message
}

fun <T> ResultWithDiagnostics<T>.getErrors(): String {
    val filteredReports = reports.filter {
        it.code != ScriptDiagnostic.incompleteCode
    }

    return filteredReports.joinToString("\n") { report ->
        report.location?.let { loc ->
            report.sourcePath?.let { sourcePath ->
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
        if (line == -1 && column == -1) ""
        else "$line:$column"
    val end =
        if (lineEnd == -1 && columnEnd == -1) ""
        else if (lineEnd == line) " - $columnEnd"
        else " - $lineEnd:$columnEnd"
    val loc = if (start.isEmpty() && end.isEmpty()) "" else " ($start$end)"
    return path + loc
}
