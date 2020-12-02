package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.api.LibraryDefinitionProducer
import org.jetbrains.kotlin.jupyter.common.ReplLineMagic
import org.jetbrains.kotlin.jupyter.compiler.util.ReplCompilerException

class MagicsProcessor(
    private val handler: MagicsHandler,
) {
    fun processMagics(code: String, parseOnly: Boolean = false, tryIgnoreErrors: Boolean = false): MagicProcessingResult {
        val magics = magicsIntervals(code)

        for (magicRange in magics) {
            // Skip `%` sign
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
        return generateSequence(MAGICS_REGEX.find(code, 0)) {
            MAGICS_REGEX.find(code, it.range.last)
        }.map { CodeInterval(it.range.first, it.range.last + 1) }
    }

    data class CodeInterval(
        /**
         * Inclusive
         */
        val from: Int,

        /**
         * Exclusive
         */
        val to: Int,
    )

    data class MagicProcessingResult(val code: String, val libraries: List<LibraryDefinitionProducer>)

    companion object {
        private val MAGICS_REGEX = Regex("^%.*$", RegexOption.MULTILINE)
    }
}
