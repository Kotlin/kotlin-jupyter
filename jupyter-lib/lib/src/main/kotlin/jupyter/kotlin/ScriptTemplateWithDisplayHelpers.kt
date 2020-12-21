package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution

abstract class ScriptTemplateWithDisplayHelpers(
    val notebook: Notebook<*>,
) {
    fun DISPLAY(value: Any) = notebook.host.display(value)

    fun EXECUTE(code: String) = notebook.host.scheduleExecution(CodeExecution(code))

    val Out: ResultsAccessor get() = notebook.results

    val JavaRuntimeUtils get() = notebook.runtimeUtils
}
