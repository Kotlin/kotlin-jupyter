package jupyter.kotlin

import jupyter.kotlin.providers.UserHandlesProvider
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

/**
 * This class is added as an implicit receiver for all compiled REPL snippets. This means
 * that all properties and functions part of this class will be visible to the user of
 * the notebook.
 */
class ScriptTemplateWithDisplayHelpers(
    val userHandlesProvider: UserHandlesProvider,
) {
    private val host: KotlinKernelHost get() = notebook.executionHost!!
    val notebook get() = userHandlesProvider.notebook

    fun DISPLAY(value: Any) = DISPLAY(value, null)

    fun DISPLAY(
        value: Any,
        id: String? = null,
    ) = host.display(value, id)

    fun UPDATE_DISPLAY(
        value: Any,
        id: String?,
    ) = host.updateDisplay(value, id)

    fun EXECUTE(code: String) = host.scheduleExecution(CodeExecution(code).toExecutionCallback())

    fun EXECUTE(executionCallback: ExecutionCallback<*>) = host.scheduleExecution(executionCallback)

    fun USE(library: LibraryDefinition) = host.scheduleExecution { addLibrary(library) }

    fun USE(libraryProducer: LibraryDefinitionProducer) =
        host.scheduleExecution {
            addLibraries(libraryProducer.getDefinitions(notebook))
        }

    fun USE(builder: JupyterIntegration.Builder.() -> Unit) = USE(createLibrary(notebook, builder))

    fun USE_STDLIB_EXTENSIONS() = host.loadStdlibJdkExtensions()

    val Out: ResultsAccessor get() = notebook.resultsAccessor
    val JavaRuntimeUtils get() = notebook.jreInfo
    val SessionOptions get() = userHandlesProvider.sessionOptions

    fun loadLibrariesByScanResult(
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

    fun loadLibraryProducers(
        vararg fqns: String,
        options: Map<String, String> = emptyMap(),
    ) = loadLibrariesByScanResult(
        LibrariesScanResult(
            producers = fqns.map { LibrariesProducerDeclaration(it) },
        ),
        options,
    )

    fun loadLibraryDefinitions(
        vararg fqns: String,
        options: Map<String, String> = emptyMap(),
    ) = loadLibrariesByScanResult(
        LibrariesScanResult(
            definitions = fqns.map { LibrariesDefinitionDeclaration(it) },
        ),
        options,
    )

    fun loadLibraryDescriptor(
        descriptorText: String,
        options: Map<String, String> = emptyMap(),
    ) {
        host.scheduleExecution {
            addLibrary(notebook.getLibraryFromDescriptor(descriptorText, options))
        }
    }

}
