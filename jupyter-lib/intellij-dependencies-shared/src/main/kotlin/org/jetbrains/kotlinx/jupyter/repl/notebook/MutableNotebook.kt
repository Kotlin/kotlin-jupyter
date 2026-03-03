package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessorInternal
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TextRenderersProcessorWithPreventingRecursion
import org.jetbrains.kotlinx.jupyter.repl.EvalData
import org.jetbrains.kotlinx.jupyter.repl.SharedReplContext
import java.nio.file.Path

interface MutableNotebook : Notebook {
    // Can be `null` before the REPL has completed initialization
    var sharedReplContext: SharedReplContext?
    override var executionHost: KotlinKernelHost?
    override var intermediateClassLoader: ClassLoader?

    override val displays: MutableDisplayContainer

    fun addCell(data: EvalData): MutableCodeCell

    fun popCell()

    fun beginEvalSession()

    fun updateFilePath(absoluteNotebookFilePath: Path)

    override val currentCell: MutableCodeCell?

    override val renderersProcessor: ResultsRenderersProcessor

    override val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion

    override val fieldsHandlersProcessor: FieldsProcessorInternal
}
