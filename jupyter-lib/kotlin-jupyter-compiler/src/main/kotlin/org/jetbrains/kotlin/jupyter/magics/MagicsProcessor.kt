package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.api.LibraryDefinitionProducer
import org.jetbrains.kotlin.jupyter.common.ReplLineMagic
import org.jetbrains.kotlin.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlin.jupyter.compiler.util.ReplCompilerException

class MagicsProcessor(
    private val handler: MagicsHandler,
    private val parseOutCellMarker: Boolean = false,
) {
    fun processMagics(code: String, parseOnly: Boolean = false, tryIgnoreErrors: Boolean = false): MagicProcessingResult {
        val magics = magicsIntervals(code)

        for (magicRange in magics) {
            if (code[magicRange.from] != MAGICS_SIGN) continue
            val magicText = code.substring(magicRange.from + 1, magicRange.to)

            try {
                val parts = magicText.split(' ', limit = 2)
                val keyword = parts[0]
                val arg = if (parts.count() > 1) parts[1] else null

                val magic = if (parseOnly) null else ReplLineMagic.valueOfOrNull(keyword)
                if (magic == null && !parseOnly && !tryIgnoreErrors) {
                    throw ReplCompilerException("Unknown line magic keyword: '$keyword'")
                }

                if (magic != null) {
                    handler.handle(magic, arg, tryIgnoreErrors, parseOnly)
                }
            } catch (e: Exception) {
                throw ReplCompilerException("Failed to process '%$magicText' command. " + e.message)
            }
        }

        val codes = codeIntervals(code, magics)
        val preprocessedCode = codes.joinToString("") { code.substring(it.from, it.to) }
        return MagicProcessingResult(preprocessedCode, handler.getLibraries())
    }

    fun codeIntervals(
        code: String,
        magicsIntervals: Sequence<CodeInterval> = magicsIntervals(code)
    ) = sequence {
        var codeStart = 0

        for (interval in magicsIntervals) {
            if (codeStart != interval.from) {
                yield(CodeInterval(codeStart, interval.from))
            }
            codeStart = interval.to
        }

        if (codeStart != code.length) {
            yield(CodeInterval(codeStart, code.length))
        }
    }

    fun magicsIntervals(code: String): Sequence<CodeInterval> {
        val maybeFirstMatch = if (parseOutCellMarker) CELL_MARKER_REGEX.find(code, 0) else null
        val seed = maybeFirstMatch ?: MAGICS_REGEX.find(code, 0)
        return generateSequence(seed) {
            MAGICS_REGEX.find(code, it.range.last)
        }.map { CodeInterval(it.range.first, it.range.last + 1) }
    }

    data class MagicProcessingResult(val code: String, val libraries: List<LibraryDefinitionProducer>)

    companion object {
        private const val MAGICS_SIGN = '%'
        private val MAGICS_REGEX = Regex("^$MAGICS_SIGN.*$", RegexOption.MULTILINE)
        private val CELL_MARKER_REGEX = Regex("""\A\s*#%%.*$""", RegexOption.MULTILINE)
    }
}
