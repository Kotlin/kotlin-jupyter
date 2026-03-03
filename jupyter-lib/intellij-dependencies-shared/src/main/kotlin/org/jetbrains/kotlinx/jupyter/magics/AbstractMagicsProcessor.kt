package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import kotlin.script.experimental.jvm.util.determineSep

open class AbstractMagicsProcessor(
    private val parseOutCellMarker: Boolean = false,
) {
    fun codeIntervals(
        code: String,
        magicsIntervals: Sequence<CodeInterval> = magicsIntervals(code),
        preserveLinesEnumeration: Boolean = false,
    ) = sequence {
        val newlineLength = code.determineSep().length
        var codeStart = 0

        for (interval in magicsIntervals) {
            if (codeStart != interval.from) {
                yield(CodeInterval(codeStart, interval.from))
            }
            codeStart = interval.to
            if (preserveLinesEnumeration && codeStart > 0 && code[codeStart - 1] == '\n') {
                codeStart -= newlineLength
            }
        }

        if (codeStart != code.length) {
            yield(CodeInterval(codeStart, code.length))
        }
    }

    fun magicsIntervals(code: String): Sequence<CodeInterval> {
        val newlineLength = code.determineSep().length
        val maybeFirstMatch = if (parseOutCellMarker) CELL_MARKER_REGEX.find(code, 0) else null
        val seed = maybeFirstMatch ?: MAGICS_REGEX.find(code, 0)
        return generateSequence(seed) {
            MAGICS_REGEX.find(code, it.range.last + 1)
        }.map {
            val start = it.range.first
            val endOfLine = it.range.last + 1
            // Include newline after this magic line in case it exists
            val end = if (endOfLine + newlineLength <= code.length) endOfLine + newlineLength else endOfLine
            CodeInterval(start, end)
        }
    }

    fun getCleanCode(
        code: String,
        magicIntervals: Sequence<CodeInterval>,
    ): String {
        val codes = codeIntervals(code, magicIntervals, true)
        return codes.joinToString("") { code.substring(it.from, it.to) }
    }

    companion object {
        const val MAGICS_SIGN = '%'
        private val MAGICS_REGEX = Regex("^$MAGICS_SIGN.*$", RegexOption.MULTILINE)
        private val CELL_MARKER_REGEX = Regex("""\A\s*#%%.*$""", RegexOption.MULTILINE)

        fun processSingleMagic(
            code: String,
            handler: MagicsHandler,
            codeInterval: CodeInterval,
            parseOnly: Boolean = false,
            tryIgnoreErrors: Boolean = false,
        ) {
            if (code[codeInterval.from] != MAGICS_SIGN) return
            val magicText = code.substring(codeInterval.from + 1, codeInterval.to).trim()
            handler.handle(magicText, tryIgnoreErrors, parseOnly)
        }
    }
}
