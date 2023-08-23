package org.jetbrains.kotlinx.jupyter.repl.workflow

import org.jetbrains.kotlinx.jupyter.api.Code

interface ExecutorWorkflowListener : EvaluatorWorkflowListener {
    fun codePreprocessed(preprocessedCode: Code)
}
