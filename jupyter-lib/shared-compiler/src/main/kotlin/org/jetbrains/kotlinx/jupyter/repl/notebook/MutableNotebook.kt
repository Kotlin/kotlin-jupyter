package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessorInternal
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TextRenderersProcessorWithPreventingRecursion
import org.jetbrains.kotlinx.jupyter.repl.EvalData
import org.jetbrains.kotlinx.jupyter.repl.SharedReplContext

interface MutableNotebook : Notebook {
    var sharedReplContext: SharedReplContext?

    override val displays: MutableDisplayContainer
    fun addCell(
        data: EvalData,
    ): MutableCodeCell

    fun popCell()

    fun beginEvalSession()

    override val currentCell: MutableCodeCell?

    override val renderersProcessor: ResultsRenderersProcessor

    override val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion

    override val fieldsHandlersProcessor: FieldsProcessorInternal
}
