package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

class MagicsProcessor(
    private val handler: LibrariesAwareMagicsHandler,
    parseOutCellMarker: Boolean = false,
) : CodePreprocessor, AbstractMagicsProcessor(parseOutCellMarker) {
    fun processMagics(code: String, parseOnly: Boolean = false, tryIgnoreErrors: Boolean = false): CodePreprocessor.Result {
        val magics = magicsIntervals(code)

        for (magicRange in magics) {
            processSingleMagic(code, handler, magicRange, parseOnly, tryIgnoreErrors)
        }

        val codes = codeIntervals(code, magics, true)
        val preprocessedCode = codes.joinToString("") { code.substring(it.from, it.to) }
        return CodePreprocessor.Result(preprocessedCode, handler.getLibraries())
    }

    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
        return processMagics(code)
    }
}
