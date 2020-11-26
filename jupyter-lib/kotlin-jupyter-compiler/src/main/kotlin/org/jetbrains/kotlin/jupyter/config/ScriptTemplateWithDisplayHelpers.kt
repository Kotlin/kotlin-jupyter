package org.jetbrains.kotlin.jupyter.config

import org.jetbrains.kotlin.jupyter.api.CodeExecution
import org.jetbrains.kotlin.jupyter.api.Notebook
import org.jetbrains.kotlin.jupyter.api.ResultsAccessor

abstract class ScriptTemplateWithDisplayHelpers(
    val notebook: Notebook<*>,
) {
    fun DISPLAY(value: Any) = notebook.host.display(value)

    fun EXECUTE(code: String) = notebook.host.scheduleExecution(CodeExecution(code))

    val Out: ResultsAccessor get() = notebook.results

    val JavaRuntimeUtils get() = notebook.runtimeUtils
}
