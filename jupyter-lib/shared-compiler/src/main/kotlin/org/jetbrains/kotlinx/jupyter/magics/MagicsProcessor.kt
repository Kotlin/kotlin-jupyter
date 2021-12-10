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
        return CodePreprocessor.Result(getCleanCode(code, magics), handler.getLibraries())
    }

    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
        return processMagics(code)
    }
}
