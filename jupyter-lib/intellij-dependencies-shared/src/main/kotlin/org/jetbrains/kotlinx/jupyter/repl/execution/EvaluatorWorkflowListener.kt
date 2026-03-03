package org.jetbrains.kotlinx.jupyter.repl.execution

interface EvaluatorWorkflowListener {
    fun internalIdGenerated(id: Int)

    fun compilationFinished()
}
