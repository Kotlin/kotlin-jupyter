package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

abstract class ScriptTemplateWithDisplayHelpers(
    val notebook: Notebook,
    private val hostProvider: KotlinKernelHostProvider
) {
    val HOST get() = hostProvider.host!!

    fun DISPLAY(value: Any) = hostProvider.host!!.display(value)

    fun EXECUTE(code: String) = hostProvider.host!!.scheduleExecution(CodeExecution(code).toExecutionCallback())

    fun USE(library: LibraryDefinition) = hostProvider.host!!.addLibrary(library)

    fun USE(builder: JupyterIntegration.Builder.() -> Unit) {
        val o = object : JupyterIntegration() {
            override fun Builder.onLoaded() {
                builder()
            }
        }
        USE(o.getDefinitions(notebook).single())
    }

    val Out: ResultsAccessor get() = ResultsAccessor { id ->
        notebook.getResult(id)
    }

    val JavaRuntimeUtils get() = notebook.jreInfo
}
