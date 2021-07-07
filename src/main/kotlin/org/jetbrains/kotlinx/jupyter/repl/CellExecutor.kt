package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

/**
 * Executes notebook cell code.
 * Performs code preprocessing (magics parsing) and snippet class postprocessing (variable converters and annotation handlers)
 */
interface CellExecutor : ExecutionHost {

    fun execute(
        code: Code,
        displayHandler: DisplayHandler? = null,
        processVariables: Boolean = true,
        processAnnotations: Boolean = true,
        processMagics: Boolean = true,
        invokeAfterCallbacks: Boolean = true,
        currentCellId: Int = 1,
        callback: ExecutionStartedCallback? = null
    ): InternalEvalResult
}

typealias ExecutionStartedCallback = (internalId: Int, code: Code) -> Unit
