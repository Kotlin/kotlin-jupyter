package org.jetbrains.kotlinx.jupyter.compiler.util

import kotlinx.serialization.Serializable
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

enum class LibraryProblemPart(val message: String) {
    PREBUILT("imports, dependencies and repositories"),
    INIT("init codes"),
    RESOURCES("resources definitions"),
    RENDERERS("renderers"),
    CONVERTERS("converters (fields callbacks)"),
    CLASS_ANNOTATIONS("class annotations callbacks"),
    FILE_ANNOTATIONS("file annotations callbacks"),
    BEFORE_CELL_CALLBACKS("initCell codes (before-cell-execution callbacks)"),
    AFTER_CELL_CALLBACKS("after-cell-execution callbacks"),
    SHUTDOWN("shutdown callbacks/codes"),
}

class ReplLibraryException(
    name: String? = null,
    val part: LibraryProblemPart,
    cause: Throwable? = null,
): ReplException("The problem is found in ${name?.let { "library $it" } ?: "one of the loaded libraries"}: check library ${part.message}", cause)

fun <T> rethrowAsLibraryException(part: LibraryProblemPart, action: () -> T): T {
    return try {
        action()
    } catch (e: Throwable) {
        throw ReplLibraryException(part = part, cause = e)
    }
}

class ReplCompilerException(errorResult: ResultWithDiagnostics.Failure? = null, message: String? = null) :
    ReplException(message ?: errorResult?.getErrors() ?: "") {

    val firstDiagnostics = errorResult?.reports?.firstOrNull {
        it.severity == ScriptDiagnostic.Severity.ERROR || it.severity == ScriptDiagnostic.Severity.FATAL
    }

    constructor(message: String) : this(null, message)
}

@Serializable
data class CompilerMessageLocationWithRange(
    val path: String,
    val line: Int,
    val column: Int,
    val lineEnd: Int,
    val columnEnd: Int,
    val lineContent: String?
) {
    override fun toString(): String =
        path + (if (line != -1 || column != -1) " ($line:$column)" else "")

    companion object {
        @JvmStatic
        fun create(
            path: String?,
            lineStart: Int,
            columnStart: Int,
            lineEnd: Int?,
            columnEnd: Int?,
            lineContent: String?
        ): CompilerMessageLocationWithRange? =
            if (path == null) null else CompilerMessageLocationWithRange(path, lineStart, columnStart, lineEnd ?: -1, columnEnd ?: -1, lineContent)
    }
}
