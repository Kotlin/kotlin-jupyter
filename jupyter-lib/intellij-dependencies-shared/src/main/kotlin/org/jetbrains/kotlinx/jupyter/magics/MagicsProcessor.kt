package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.exceptions.KernelInternalObject

/**
 * This class handles all magic commands encountered during compilation of a notebook cell, i.e., commands that
 * start with `%`, like `%use dataframe`.
 *
 * If this results in new classes that need to be added to the classpath, they are returned in
 * [CodePreprocessor.Result.libraries].
 */
class MagicsProcessor(
    private val handler: LibrariesAwareMagicsHandler,
    parseOutCellMarker: Boolean = false,
) : AbstractMagicsProcessor(parseOutCellMarker),
    CodePreprocessor,
    KernelInternalObject {
    fun processMagics(
        code: String,
        parseOnly: Boolean = false,
        tryIgnoreErrors: Boolean = false,
    ): CodePreprocessor.Result {
        val magics = magicsIntervals(code)

        for (magicRange in magics) {
            processSingleMagic(code, handler, magicRange, parseOnly, tryIgnoreErrors)
        }
        return CodePreprocessor.Result(getCleanCode(code, magics), handler.getLibraries())
    }

    override fun process(
        code: String,
        host: KotlinKernelHost,
    ): CodePreprocessor.Result = processMagics(code)
}
