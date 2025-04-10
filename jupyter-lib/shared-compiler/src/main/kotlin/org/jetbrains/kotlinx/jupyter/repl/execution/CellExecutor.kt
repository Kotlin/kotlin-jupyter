package org.jetbrains.kotlinx.jupyter.repl.execution

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.config.CellId
import org.jetbrains.kotlinx.jupyter.repl.result.InternalEvalResult

/**
 * This interface is responsible for executing the code found inside a single notebook cell.
 * It also performs code preprocessing (magics parsing) and snippet class postprocessing (variable converters and
 * annotation handlers).
 */
interface CellExecutor : ExecutionHost {
    fun execute(
        code: Code,
        processVariables: Boolean = true,
        processAnnotations: Boolean = true,
        processMagics: Boolean = true,
        invokeAfterCallbacks: Boolean = true,
        isUserCode: Boolean = false,
        currentCellId: CellId = CellId.NO_CELL,
        stackFrame: ExecutionStackFrame? = null,
        executorWorkflowListener: ExecutorWorkflowListener? = null,
    ): InternalEvalResult
}
