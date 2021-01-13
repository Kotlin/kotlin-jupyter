package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

abstract class ScriptTemplateWithDisplayHelpers(
    val notebook: Notebook<*>,
    private val hostProvider: KotlinKernelHostProvider
) {
    fun DISPLAY(value: Any) = hostProvider.host!!.display(value)

    fun EXECUTE(code: String) = hostProvider.host!!.scheduleExecution(CodeExecution(code))

    fun USE(library: LibraryDefinition) = hostProvider.host!!.addLibrary(library)

    fun USE(builder: JupyterIntegration.Builder.(Notebook<*>?) -> Unit) {
        val o = object : JupyterIntegration(builder) {}
        USE(o.getDefinitions(null).single())
    }

    val Out: ResultsAccessor get() = notebook.results

    val JavaRuntimeUtils get() = notebook.jreInfo
}
