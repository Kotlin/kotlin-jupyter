package org.jetbrains.kotlinx.jupyter.repl.execution

import org.jetbrains.kotlinx.jupyter.api.Code

interface ExecutorWorkflowListener : EvaluatorWorkflowListener {
    fun codePreprocessed(preprocessedCode: Code)
}
