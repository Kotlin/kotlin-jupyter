package org.jetbrains.kotlinx.jupyter.repl.workflow

interface EvaluatorWorkflowListener {
    fun internalIdGenerated(id: Int)

    fun compilationFinished()
}
