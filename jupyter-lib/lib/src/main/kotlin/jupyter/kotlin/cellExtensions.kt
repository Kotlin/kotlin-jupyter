package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesDefinitionDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesProducerDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesScanResult
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.api.libraries.createLibrary

private val ScriptTemplateWithDisplayHelpers.host: KotlinKernelHost get() = userHandlesProvider.host!!
val ScriptTemplateWithDisplayHelpers.notebook get() = userHandlesProvider.notebook
fun ScriptTemplateWithDisplayHelpers.DISPLAY(value: Any) = DISPLAY(value, null)
fun ScriptTemplateWithDisplayHelpers.DISPLAY(value: Any, id: String? = null) = host.display(value, id)
fun ScriptTemplateWithDisplayHelpers.UPDATE_DISPLAY(value: Any, id: String?) = host.updateDisplay(value, id)
fun ScriptTemplateWithDisplayHelpers.EXECUTE(code: String) = host.scheduleExecution(CodeExecution(code).toExecutionCallback())
fun ScriptTemplateWithDisplayHelpers.EXECUTE(executionCallback: ExecutionCallback<*>) = host.scheduleExecution(executionCallback)

fun ScriptTemplateWithDisplayHelpers.USE(library: LibraryDefinition) = host.scheduleExecution { addLibrary(library) }
fun ScriptTemplateWithDisplayHelpers.USE(libraryProducer: LibraryDefinitionProducer) = host.scheduleExecution { addLibraries(libraryProducer.getDefinitions(notebook)) }
fun ScriptTemplateWithDisplayHelpers.USE(builder: JupyterIntegration.Builder.() -> Unit) = USE(createLibrary(notebook, builder))

fun ScriptTemplateWithDisplayHelpers.USE_STDLIB_EXTENSIONS() = host.loadStdlibJdkExtensions()
val ScriptTemplateWithDisplayHelpers.Out: ResultsAccessor get() = notebook.resultsAccessor
val ScriptTemplateWithDisplayHelpers.JavaRuntimeUtils get() = notebook.jreInfo
val ScriptTemplateWithDisplayHelpers.SessionOptions get() = userHandlesProvider.sessionOptions

fun ScriptTemplateWithDisplayHelpers.loadLibrariesByScanResult(
    scanResult: LibrariesScanResult,
    options: Map<String, String> = emptyMap(),
) {
    notebook.libraryLoader.addLibrariesByScanResult(
        host,
        notebook,
        host.lastClassLoader,
        options,
        scanResult,
    )
}

fun ScriptTemplateWithDisplayHelpers.loadLibraryProducers(
    vararg fqns: String,
    options: Map<String, String> = emptyMap(),
) = loadLibrariesByScanResult(
    LibrariesScanResult(
        producers = fqns.map { LibrariesProducerDeclaration(it) },
    ),
    options,
)

fun ScriptTemplateWithDisplayHelpers.loadLibraryDefinitions(
    vararg fqns: String,
    options: Map<String, String> = emptyMap(),
) = loadLibrariesByScanResult(
    LibrariesScanResult(
        definitions = fqns.map { LibrariesDefinitionDeclaration(it) },
    ),
    options,
)

fun ScriptTemplateWithDisplayHelpers.loadLibraryDescriptor(
    descriptorText: String,
    options: Map<String, String> = emptyMap(),
) {
    host.scheduleExecution {
        addLibrary(notebook.getLibraryFromDescriptor(descriptorText, options))
    }
}
