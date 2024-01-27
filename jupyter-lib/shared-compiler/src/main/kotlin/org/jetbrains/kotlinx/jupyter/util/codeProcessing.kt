package org.jetbrains.kotlinx.jupyter.util

import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.determineSep
import kotlin.script.experimental.jvm.util.toSourceCodePosition

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

fun SourceCode.Position.withNewAbsolute(code: SourceCode, newCode: SourceCode): SourceCode.Position? {
    val sep = code.text.determineSep()
    val absLineStart =
        if (line == 1) 0
        else newCode.text.findNthSubstring(sep, line - 1) + sep.length

    var nextNewLinePos = newCode.text.indexOf(sep, absLineStart)
    if (nextNewLinePos == -1) nextNewLinePos = newCode.text.length

    val abs = absLineStart + col - 1
    if (abs > nextNewLinePos) {
        return null
    }

    return SourceCode.Position(line, col, abs)
}

fun Int.toSourceCodePositionWithNewAbsolute(code: SourceCode, newCode: SourceCode): SourceCode.Position? {
    return toSourceCodePosition(code).withNewAbsolute(code, newCode)
}
