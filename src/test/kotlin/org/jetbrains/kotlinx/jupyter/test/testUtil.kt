package org.jetbrains.kotlinx.jupyter.test

import io.kotest.assertions.fail
import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.EvalRequestData
import org.jetbrains.kotlinx.jupyter.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.MutableNotebook
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.api.HtmlData
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.LibraryLoader
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.TextRenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.api.libraries.createLibrary
import org.jetbrains.kotlinx.jupyter.api.withId
import org.jetbrains.kotlinx.jupyter.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.AbstractLibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.ChainedLibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptors
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.messaging.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.creating.MockJupyterConnection
import org.jetbrains.kotlinx.jupyter.repl.renderValue
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext
import kotlin.test.assertEquals

val testDataDir = File("src/test/testData")

const val standardResolverBranch = "master"

val testRepositories = defaultRepositoriesCoordinates

val standardResolverRuntimeProperties = object : ReplRuntimeProperties by defaultRuntimeProperties {
    override val currentBranch: String
        get() = standardResolverBranch
}

val classpath = scriptCompilationClasspathFromContext(
    "lib",
    "api",
    "shared-compiler",
    "kotlin-stdlib",
    "kotlin-reflect",
    "kotlin-script-runtime",
    "kotlinx-serialization-json-jvm",
    "kotlinx-serialization-core-jvm",
    classLoader = TestDisplayHandler::class.java.classLoader,
)

val KClass<*>.classPathEntry: File get() {
    return File(this.java.protectionDomain.codeSource.location.toURI().path)
}

inline fun <reified T> classPathEntry(): File {
    return (typeOf<T>().classifier as KClass<*>).classPathEntry
}

val testLibraryResolver: LibraryResolver
    get() = getResolverFromNamesMap(parseLibraryDescriptors(readLibraries()))

fun assertUnit(value: Any?) = assertEquals(Unit, value)

fun assertStartsWith(expectedPrefix: String, actual: String) {
    if (actual.startsWith(expectedPrefix)) return
    val actualStart = actual.substring(0, minOf(expectedPrefix.length, actual.length))
    throw AssertionError("Expected a string to start with '$expectedPrefix', but it starts with '$actualStart")
}

fun Collection<Pair<String, String>>.toLibraries(): LibraryResolver {
    val libJsons = associate { it.first to it.second }
    return getResolverFromNamesMap(parseLibraryDescriptors(libJsons))
}

@JvmName("toLibrariesStringLibraryDefinition")
fun Collection<Pair<String, LibraryDefinition>>.toLibraries() = getResolverFromNamesMap(definitions = toMap())

fun getResolverFromNamesMap(
    descriptors: Map<String, LibraryDescriptor>? = null,
    definitions: Map<String, LibraryDefinition>? = null,
): LibraryResolver {
    return InMemoryLibraryResolver(
        null,
        descriptors?.mapKeys { entry -> LibraryReference(AbstractLibraryResolutionInfo.Default(), entry.key) },
        definitions?.mapKeys { entry -> LibraryReference(AbstractLibraryResolutionInfo.Default(), entry.key) },
    )
}

fun readLibraries(basePath: String? = null): Map<String, String> {
    return KERNEL_LIBRARIES.homeLibrariesDir(basePath?.let(::File))
        .listFiles()?.filter(KERNEL_LIBRARIES::isLibraryDescriptor)
        ?.map {
            log.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
            it.nameWithoutExtension to it.readText()
        }
        .orEmpty()
        .toMap()
}

fun CompletionResult.getOrFail(): CompletionResult.Success = when (this) {
    is CompletionResult.Success -> this
    else -> fail("Result should be success")
}

fun Map<String, VariableState>.mapToStringValues(): Map<String, String?> {
    return mapValues { it.value.stringValue }
}

fun Map<String, VariableState>.getStringValue(variableName: String): String? {
    return get(variableName)?.stringValue
}

fun Map<String, VariableState>.getValue(variableName: String): Any? {
    return get(variableName)?.value?.getOrNull()
}

class InMemoryLibraryResolver(
    parent: LibraryResolver?,
    initialDescriptorsCache: Map<LibraryReference, LibraryDescriptor>? = null,
    initialDefinitionsCache: Map<LibraryReference, LibraryDefinition>? = null,
) : ChainedLibraryResolver(parent) {
    private val definitionsCache = hashMapOf<LibraryReference, LibraryDefinition>()
    private val descriptorsCache = hashMapOf<LibraryReference, LibraryDescriptor>()

    init {
        initialDescriptorsCache?.forEach { (key, value) ->
            descriptorsCache[key] = value
        }
        initialDefinitionsCache?.forEach { (key, value) ->
            definitionsCache[key] = value
        }
    }

    override fun shouldResolve(reference: LibraryReference): Boolean {
        return reference.shouldBeCachedInMemory
    }

    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        return definitionsCache[reference] ?: descriptorsCache[reference]?.convertToDefinition(arguments)
    }

    override fun save(reference: LibraryReference, definition: LibraryDefinition) {
        definitionsCache[reference] = definition
    }
}

open class TestDisplayHandler(val list: MutableList<Any> = mutableListOf()) : DisplayHandler {
    override fun handleDisplay(value: Any, host: ExecutionHost, id: String?) {
        list.add(value)
    }

    override fun handleUpdate(value: Any, host: ExecutionHost, id: String?) {
        TODO("Not yet implemented")
    }
}

class TestDisplayHandlerWithRendering(
    private val notebook: MutableNotebook,
    list: MutableList<Any> = mutableListOf(),
) : TestDisplayHandler(list) {

    override fun handleDisplay(value: Any, host: ExecutionHost, id: String?) {
        super.handleDisplay(value, host, id)
        val display = renderValue(notebook, host, value)?.let { if (id != null) it.withId(id) else it } ?: return
        notebook.currentCell?.addDisplay(display)
    }

    override fun handleUpdate(value: Any, host: ExecutionHost, id: String?) {
        super.handleUpdate(value, host, id)
        val display = renderValue(notebook, host, value) ?: return
        val container = notebook.displays
        container.update(id, display)
        container.getById(id).distinctBy { it.cell.id }.forEach {
            it.cell.displays.update(id, display)
        }
    }
}

object NotebookMock : Notebook {
    private val cells = hashMapOf<Int, MutableCodeCell>()

    override val cellsList: Collection<CodeCell>
        get() = emptyList()
    override val variablesState = mutableMapOf<String, VariableStateImpl>()
    override val cellVariables = mapOf<Int, Set<String>>()
    override val resultsAccessor = ResultsAccessor { getResult(it) }

    override fun getCell(id: Int): MutableCodeCell {
        return cells[id] ?: throw ArrayIndexOutOfBoundsException(
            "There is no cell with number '$id'",
        )
    }

    override fun getResult(id: Int): Any? {
        return getCell(id).result
    }

    override val displays: DisplayContainer
        get() = error("Not supposed to be called")

    override fun getAllDisplays(): List<DisplayResultWithCell> {
        return displays.getAll()
    }

    override fun getDisplaysById(id: String?): List<DisplayResultWithCell> {
        return displays.getById(id)
    }

    override fun history(before: Int): CodeCell? {
        error("Not supposed to be called")
    }

    override val currentColorScheme: ColorScheme?
        get() = null

    override fun changeColorScheme(newScheme: ColorScheme) {
        error("Not supposed to be called")
    }

    override fun renderHtmlAsIFrame(data: HtmlData): MimeTypedResult {
        error("Not supposed to be called")
    }

    override val kernelVersion: KotlinKernelVersion
        get() = defaultRuntimeProperties.version!!
    override val jreInfo: JREInfoProvider
        get() = JavaRuntime

    override val renderersProcessor: RenderersProcessor
        get() = error("Not supposed to be called")

    override val textRenderersProcessor: TextRenderersProcessor
        get() = error("Not supposed to be called")

    override val fieldsHandlersProcessor: FieldsProcessor
        get() = error("Not supposed to be called")

    override val beforeCellExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
        get() = error("Not supposed to be called")

    override val afterCellExecutionsProcessor: ExtensionsProcessor<AfterCellExecutionCallback>
        get() = error("Not supposed to be called")

    override val shutdownExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
        get() = error("Not supposed to be called")

    override val codePreprocessorsProcessor: ExtensionsProcessor<CodePreprocessor>
        get() = error("Not supposed to be called")
    override val interruptionCallbacksProcessor: ExtensionsProcessor<InterruptionCallback>
        get() = error("Not supposed to be called")
    override val colorSchemeChangeCallbacksProcessor: ExtensionsProcessor<ColorSchemeChangedCallback>
        get() = error("Not supposed to be called")

    override val libraryRequests: Collection<LibraryResolutionRequest>
        get() = error("Not supposed to be called")

    override val libraryLoader: LibraryLoader
        get() = error("Not supposed to be called")

    override fun getLibraryFromDescriptor(descriptorText: String, options: Map<String, String>): LibraryDefinition {
        error("Not supposed to be called")
    }

    override val jupyterClientType: JupyterClientType
        get() = JupyterClientType.UNKNOWN

    override val connection: JupyterConnection
        get() = MockJupyterConnection

    override val commManager: CommManager
        get() = CommManagerImpl(MockJupyterConnection)
}

fun library(builder: JupyterIntegration.Builder.() -> Unit) = createLibrary(NotebookMock, builder)

fun ReplForJupyter.evalEx(code: Code) = evalEx(EvalRequestData(code))

fun ReplForJupyter.evalRaw(code: Code): Any? {
    return evalEx(code).rawValue
}

fun ReplForJupyter.evalRendered(code: Code): Any? {
    return evalEx(code).renderedValue
}
