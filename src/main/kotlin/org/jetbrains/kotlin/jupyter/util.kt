package org.jetbrains.kotlin.jupyter

import kotlinx.coroutines.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.utils.addToStdlib.min
import org.slf4j.Logger
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.determineSep
import kotlin.script.experimental.jvm.util.toSourceCodePosition

fun <T> catchAll(body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    null
}

fun <T> Logger.catchAll(msg: String = "", body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    this.error(msg, e)
    null
}

fun <T> T.validOrNull(predicate: (T) -> Boolean): T? = if (predicate(this)) this else null

fun <T> T.asAsync(): Deferred<T> = GlobalScope.async { this@asAsync }

fun File.existsOrNull() = if (exists()) this else null

fun <T> Deferred<T>.awaitBlocking(): T = if (isCompleted) getCompleted() else runBlocking { await() }

fun String.parseIniConfig() =
        lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun List<String>.joinToLines() = joinToString("\n")

fun File.tryReadIniConfig() =
        existsOrNull()?.let {
            catchAll { it.readText().parseIniConfig() }
        }


fun generateDiagnostic(fromLine: Int, fromCol: Int, toLine: Int, toCol: Int, message: String, severity: String) =
        ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedError,
                message,
                ScriptDiagnostic.Severity.valueOf(severity),
                null,
                SourceCode.Location(SourceCode.Position(fromLine, fromCol), SourceCode.Position(toLine, toCol))
        )

fun withPath(path: String?, diagnostics: List<ScriptDiagnostic>): List<ScriptDiagnostic> =
        diagnostics.map { it.copy(sourcePath = path) }

internal fun <T> ResultWithDiagnostics<T>.getErrors(): String {
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

fun String.findNthSubstring(s: String, n: Int, start: Int = 0): Int {
    if (n < 1 || start == -1) return -1

    var i = start

    for (k in 1..n) {
        i = indexOf(s, i)
        if (i == -1) return -1
        i += s.length
    }

    return i - s.length
}

fun Int.toSourceCodePositionWithNewAbsolute(code: SourceCode, newCode: SourceCode): SourceCode.Position? {
    val pos = toSourceCodePosition(code)
    val sep = code.text.determineSep()
    val absLineStart =
            if (pos.line == 1) 0
            else newCode.text.findNthSubstring(sep, pos.line - 1) + sep.length

    var nextNewLinePos = newCode.text.indexOf(sep, absLineStart)
    if (nextNewLinePos == -1) nextNewLinePos = newCode.text.length

    val abs = absLineStart + pos.col - 1
    if (abs > nextNewLinePos)
        return null

    return SourceCode.Position(pos.line, abs - absLineStart + 1, abs)
}
