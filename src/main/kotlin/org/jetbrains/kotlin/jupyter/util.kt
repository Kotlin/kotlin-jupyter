package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.jupyter.compiler.util.SourceCodeImpl
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.determineSep
import kotlin.script.experimental.jvm.util.toSourceCodePosition

fun String.parseIniConfig() =
    lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun List<String>.joinToLines() = joinToString("\n")

fun generateDiagnostic(fromLine: Int, fromCol: Int, toLine: Int, toCol: Int, message: String, severity: String) =
    ScriptDiagnostic(
        ScriptDiagnostic.unspecifiedError,
        message,
        ScriptDiagnostic.Severity.valueOf(severity),
        null,
        SourceCode.Location(SourceCode.Position(fromLine, fromCol), SourceCode.Position(toLine, toCol))
    )

fun generateDiagnosticFromAbsolute(code: String, from: Int, to: Int, message: String, severity: String): ScriptDiagnostic {
    val snippet = SourceCodeImpl(0, code)
    return ScriptDiagnostic(
        ScriptDiagnostic.unspecifiedError,
        message,
        ScriptDiagnostic.Severity.valueOf(severity),
        null,
        SourceCode.Location(from.toSourceCodePosition(snippet), to.toSourceCodePosition(snippet))
    )
}

fun withPath(path: String?, diagnostics: List<ScriptDiagnostic>): List<ScriptDiagnostic> =
    diagnostics.map { it.copy(sourcePath = path) }

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
