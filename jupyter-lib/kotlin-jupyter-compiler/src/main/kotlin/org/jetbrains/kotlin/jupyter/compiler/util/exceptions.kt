package org.jetbrains.kotlin.jupyter.compiler.util

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

fun <T> ResultWithDiagnostics<T>.getErrors(): String {
    val filteredReports = reports.filter {
        it.code != ScriptDiagnostic.incompleteCode
    }

    return filteredReports.joinToString("\n") { report ->
        report.location?.let { loc ->
            CompilerMessageLocationWithRange.create(
                report.sourcePath,
                loc.start.line,
                loc.start.col,
                loc.end?.line,
                loc.end?.col,
                null
            )?.toExtString()?.let {
                "$it "
            }
        }.orEmpty() + report.message
    }
}

fun CompilerMessageLocationWithRange.toExtString(): String {
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

open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReplCompilerException(errorResult: ResultWithDiagnostics.Failure? = null, message: String? = null) :
    ReplException(message ?: errorResult?.getErrors() ?: "") {

    val firstDiagnostics = errorResult?.reports?.firstOrNull {
        it.severity == ScriptDiagnostic.Severity.ERROR || it.severity == ScriptDiagnostic.Severity.FATAL
    }

    constructor(message: String) : this(null, message)
}
