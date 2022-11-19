package jupyter.kotlin

import jupyter.kotlin.providers.UserHandlesProvider
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

abstract class ScriptTemplateWithDisplayHelpers(
    val userHandlesProvider: UserHandlesProvider
) {
    private val host: KotlinKernelHost get() = userHandlesProvider.host!!

    val notebook get() = userHandlesProvider.notebook

    fun DISPLAY(value: Any) = DISPLAY(value, null)

    fun DISPLAY(value: Any, id: String? = null) = host.display(value, id)

    fun UPDATE_DISPLAY(value: Any, id: String?) = host.updateDisplay(value, id)

    fun EXECUTE(code: String) = host.scheduleExecution(CodeExecution(code).toExecutionCallback())

    fun USE(library: LibraryDefinition) = host.scheduleExecution { addLibrary(library) }

    fun USE(builder: JupyterIntegration.Builder.() -> Unit) {
        val o = object : JupyterIntegration() {
            override fun Builder.onLoaded() {
                builder()
            }
        }
        USE(o.getDefinitions(notebook).single())
    }

    fun USE_STDLIB_EXTENSIONS() = host.loadStdlibJdkExtensions()

    val Out: ResultsAccessor get() = notebook.resultsAccessor

    val JavaRuntimeUtils get() = notebook.jreInfo

    val SessionOptions get() = userHandlesProvider.sessionOptions
}
