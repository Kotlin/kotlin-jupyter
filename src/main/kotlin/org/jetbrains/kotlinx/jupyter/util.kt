package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.bufferedImageRenderer
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.determineSep
import kotlin.script.experimental.jvm.util.toSourceCodePosition

fun List<String>.joinToLines() = joinToString("\n")

// todo : perhaps, create factory class
fun generateHTMLVarsReport(variablesMap: Map<String, VariableState>): String {
    val stringBuilder = StringBuilder(
        """
            <!DOCTYPE html>
            <html>
            <head>
            
        """.trimIndent()
    )
    stringBuilder.append(generateStyleSection())
    stringBuilder.append("\n</head>\n<body>\n")
    stringBuilder.append("<h2 style=\"text-align:center\">Variables State</h2>\n")
    if (variablesMap.isEmpty()) {
        stringBuilder.append("<p>Empty state</p>\n\n")
        stringBuilder.append("</body>\n</html>")
        return stringBuilder.toString()
    }

    stringBuilder.append(generateVarsTable(variablesMap))

    stringBuilder.append("</body>\n</html>")
    return stringBuilder.toString()
}

// todo: text is not aligning in a center
fun generateStyleSection(borderPx: Int = 1, paddingPx: Int = 5): String {
    return """
    <style>
    table, th, td {
      border: ${borderPx}px solid black;
      border-collapse: collapse;
      text-align:center;
    }
    th, td {
      padding: ${paddingPx}px;
    }
    </style>
    """.trimIndent()
}

fun generateVarsTable(variablesMap: Map<String, VariableState>): String {
    val tableBuilder = StringBuilder(
        """
    <table style="width:80%" align="center">
      <tr>
        <th>Variable</th>
        <th>Value</th>
      </tr>
      
        """.trimIndent()
    )

    variablesMap.entries.forEach {
        tableBuilder.append(
            """
        <tr>
            <td>${it.key}</td>
            <td>${it.value.stringValue}</td>
        </tr>
            """.trimIndent()
        )
    }

    return tableBuilder.append("\n</table>\n").toString()
}

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
    if (abs > nextNewLinePos) {
        return null
    }

    return SourceCode.Position(pos.line, abs - absLineStart + 1, abs)
}

fun ResultsRenderersProcessor.registerDefaultRenderers() {
    register(bufferedImageRenderer)
}
