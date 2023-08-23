package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.impl.ExecutionStackFrame
import org.jetbrains.kotlinx.jupyter.repl.workflow.ExecutorWorkflowListener

/**
 * Executes notebook cell code.
 * Performs code preprocessing (magics parsing) and snippet class postprocessing (variable converters and annotation handlers)
 */
interface CellExecutor : ExecutionHost {

    fun execute(
        code: Code,
        displayHandler: DisplayHandler = NoOpDisplayHandler,
        processVariables: Boolean = true,
        processAnnotations: Boolean = true,
        processMagics: Boolean = true,
        invokeAfterCallbacks: Boolean = true,
        isUserCode: Boolean = false,
        currentCellId: Int = -1,
        stackFrame: ExecutionStackFrame? = null,
        executorWorkflowListener: ExecutorWorkflowListener? = null,
    ): InternalEvalResult
}
