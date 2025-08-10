package org.jetbrains.kotlinx.jupyter.test

import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.api.HtmlData
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.LibraryLoader
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.api.StandaloneKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.TextRenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.api.libraries.createLibrary
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.withId
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.common.SimpleHttpClient
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.config.errorForUser
import org.jetbrains.kotlinx.jupyter.libraries.AbstractLibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.ChainedLibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptors
import org.jetbrains.kotlinx.jupyter.messaging.CommunicationFacilityMock
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplComponentsProviderBase
import org.jetbrains.kotlinx.jupyter.repl.notebook.DisplayResultWrapper
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableDisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.renderValue
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.util.asCommonFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

val testDataDir = File("src/test/testData")

const val STANDARD_RESOLVER_BRANCH = "master"

val testRepositories = defaultRepositoriesCoordinates

val standardResolverRuntimeProperties =
    object : ReplRuntimeProperties by defaultRuntimeProperties {
        override val currentBranch: String
            get() = STANDARD_RESOLVER_BRANCH
    }

val testLoggerFactory: KernelLoggerFactory = DefaultKernelLoggerFactory

val classpath =
    scriptCompilationClasspathFromContext(
        "lib",
        "api",
        "protocol-api",
        "slf4j-api",
        "shared-compiler",
        "kotlin-stdlib",
        "kotlin-reflect",
        "kotlin-script-runtime",
        "kotlinx-serialization-json-jvm",
        "kotlinx-serialization-core-jvm",
        classLoader = TestDisplayHandler::class.java.classLoader,
    )

val KClass<*>.classPathEntry: File get() {
    return File(
        this.java.protectionDomain.codeSource.location
            .toURI()
            .path,
    )
}

inline fun <reified T> classPathEntry(): File = (typeOf<T>().classifier as KClass<*>).classPathEntry

val testLibraryResolver: LibraryResolver
    get() =
        getResolverFromNamesMap(
            parseLibraryDescriptors(testLoggerFactory, readLibraries()),
        )

val KERNEL_LIBRARIES =
    LibraryDescriptorsManager.getInstance(
        SimpleHttpClient,
        testLoggerFactory.asCommonFactory(),
    ) { logger, message, exception ->
        logger.errorForUser(message = message, throwable = exception)
    }

fun Any?.shouldBeUnit() = this shouldBe Unit

fun assertStartsWith(
    expectedPrefix: CharSequence,
    actual: String,
) {
    if (actual.startsWith(expectedPrefix)) return
    val actualStart = actual.take(minOf(expectedPrefix.length, actual.length))
    throw AssertionError("Expected a string to start with '$expectedPrefix', but it starts with '$actualStart")
}

fun Collection<Pair<String, String>>.toLibraries(): LibraryResolver {
    val libJsons = associate { it.first to it.second }
    return getResolverFromNamesMap(
        parseLibraryDescriptors(testLoggerFactory, libJsons),
    )
}

@JvmName("toLibrariesStringLibraryDefinition")
fun Collection<Pair<String, LibraryDefinition>>.toLibraries() = getResolverFromNamesMap(definitions = toMap())

fun getResolverFromNamesMap(
    descriptors: Map<String, LibraryDescriptor>? = null,
    definitions: Map<String, LibraryDefinition>? = null,
): LibraryResolver =
    InMemoryLibraryResolver(
        null,
        descriptors?.mapKeys { entry -> LibraryReference(AbstractLibraryResolutionInfo.Default(), entry.key) },
        definitions?.mapKeys { entry -> LibraryReference(AbstractLibraryResolutionInfo.Default(), entry.key) },
    )

fun readLibraries(basePath: String? = null): Map<String, String> {
    val logger = testLoggerFactory.getLogger("test")

    return KERNEL_LIBRARIES
        .homeLibrariesDir(basePath?.let(::File))
        .listFiles()
        ?.filter(KERNEL_LIBRARIES::isLibraryDescriptor)
        ?.map {
            logger.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
            it.nameWithoutExtension to it.readText()
        }.orEmpty()
        .toMap()
}

fun CompletionResult.getOrFail(): CompletionResult.Success =
    when (this) {
        is CompletionResult.Success -> this
        else -> fail("Result should be success")
    }

fun Map<String, VariableState>.mapToStringValues(): Map<String, String?> = mapValues { it.value.stringValue }

fun Map<String, VariableState>.getStringValue(variableName: String): String? = get(variableName)?.stringValue

fun Map<String, VariableState>.getValue(variableName: String): Any? = get(variableName)?.value?.getOrNull()

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

    override fun shouldResolve(reference: LibraryReference): Boolean = reference.shouldBeCachedInMemory

    override fun tryResolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? = definitionsCache[reference] ?: descriptorsCache[reference]?.convertToDefinition(arguments)

    override fun save(
        reference: LibraryReference,
        definition: LibraryDefinition,
    ) {
        definitionsCache[reference] = definition
    }
}

open class TestDisplayHandler(
    val list: MutableList<Any> = mutableListOf(),
) : DisplayHandler {
    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        list.add(value)
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        TODO("Not yet implemented")
    }
}

open class TestDisplayHandlerWithRendering(
    private val notebook: MutableNotebook,
) : DisplayHandler {
    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = renderValue(notebook, host, value, id)?.let { if (id != null) it.withId(id) else it } ?: return
        notebook.currentCell?.addDisplay(display)
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = renderValue(notebook, host, value, id) ?: return
        val container = notebook.displays
        container.update(id, display)
        container.getById(id).distinctBy { it.cell.id }.forEach {
            it.cell.displays.update(id, display)
        }
    }
}

object NotebookMock : Notebook {
    override val workingDir: Path = Path.of("")

    override val intermediateClassLoader: ClassLoader
        get() = notImplemented()

    override val executionHost: KotlinKernelHost
        get() = notImplemented()

    private val cells = hashMapOf<Int, MutableCodeCell>()

    override val cellsList: Collection<CodeCell>
        get() = emptyList()
    override val variablesState = mutableMapOf<String, VariableStateImpl>()
    override val cellVariables = mapOf<Int, Set<String>>()
    override val resultsAccessor = ResultsAccessor { getResult(it) }
    override val currentClasspath: List<String>
        get() = notImplemented()

    override val sessionOptions: SessionOptions
        get() = notImplemented()

    override val loggerFactory: KernelLoggerFactory get() = DefaultKernelLoggerFactory

    override fun getCell(id: Int): MutableCodeCell =
        cells[id] ?: throw ArrayIndexOutOfBoundsException(
            "There is no cell with number '$id'",
        )

    override fun getResult(id: Int): Any? = getCell(id).result

    override val displays: DisplayContainer
        get() = notImplemented()

    override fun getAllDisplays(): List<DisplayResultWithCell> = displays.getAll()

    override fun getDisplaysById(id: String?): List<DisplayResultWithCell> = displays.getById(id)

    override fun history(before: Int): CodeCell {
        notImplemented()
    }

    override val currentColorScheme: ColorScheme?
        get() = null

    override fun changeColorScheme(newScheme: ColorScheme) {
        notImplemented()
    }

    override fun renderHtmlAsIFrame(data: HtmlData): MimeTypedResult {
        notImplemented()
    }

    override val kernelVersion: KotlinKernelVersion
        get() = defaultRuntimeProperties.version!!
    override val jreInfo: JREInfoProvider
        get() = JavaRuntime

    override val renderersProcessor: RenderersProcessor
        get() = notImplemented()

    override val textRenderersProcessor: TextRenderersProcessor
        get() = notImplemented()

    override val throwableRenderersProcessor: ThrowableRenderersProcessor
        get() = notImplemented()

    override val fieldsHandlersProcessor: FieldsProcessor
        get() = notImplemented()

    override val beforeCellExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
        get() = notImplemented()

    override val afterCellExecutionsProcessor: ExtensionsProcessor<AfterCellExecutionCallback>
        get() = notImplemented()

    override val shutdownExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
        get() = notImplemented()

    override val codePreprocessorsProcessor: ExtensionsProcessor<CodePreprocessor>
        get() = notImplemented()
    override val interruptionCallbacksProcessor: ExtensionsProcessor<InterruptionCallback>
        get() = notImplemented()
    override val colorSchemeChangeCallbacksProcessor: ExtensionsProcessor<ColorSchemeChangedCallback>
        get() = notImplemented()

    override val libraryRequests: Collection<LibraryResolutionRequest>
        get() = notImplemented()

    override val libraryLoader: LibraryLoader
        get() = notImplemented()

    override fun getLibraryFromDescriptor(
        descriptorText: String,
        options: Map<String, String>,
    ): LibraryDefinition {
        notImplemented()
    }

    private fun notImplemented(): Nothing {
        error("Not supposed to be called")
    }

    override val jupyterClientType: JupyterClientType
        get() = JupyterClientType.UNKNOWN

    override val kernelRunMode: KernelRunMode
        get() = StandaloneKernelRunMode

    override val commManager: CommManager
        get() = CommManagerImpl(CommunicationFacilityMock)

    override fun prompt(
        prompt: String,
        isPassword: Boolean,
    ): String {
        notImplemented()
    }
}

object ReplComponentsProviderMock : ReplComponentsProviderBase()

fun library(builder: JupyterIntegration.Builder.() -> Unit) = createLibrary(NotebookMock, builder)

fun ReplForJupyter.evalEx(code: Code) = evalEx(EvalRequestData(code))

inline fun <reified T : Throwable> ReplForJupyter.evalError(code: Code): T {
    val result = evalEx(code)
    result.shouldBeInstanceOf<EvalResultEx.Error>()
    return result.error.shouldBeTypeOf()
}

inline fun <reified T : Throwable> ReplForJupyter.evalRenderedError(code: Code): DisplayResult? {
    val result = evalEx(code)
    result.shouldBeInstanceOf<EvalResultEx.RenderedError>()
    result.error.shouldBeTypeOf<T>()

    return result.displayError
}

fun ReplForJupyter.evalInterrupted(code: Code) {
    val result = evalEx(code)
    result.shouldBeInstanceOf<EvalResultEx.Interrupted>()
}

fun ReplForJupyter.evalExSuccess(code: Code): EvalResultEx.Success {
    val result = evalEx(code)
    result.shouldBeInstanceOf<EvalResultEx.Success>()
    return result
}

fun ReplForJupyter.evalRaw(code: Code): Any? = evalExSuccess(code).internalResult.result.value

fun ReplForJupyter.evalRendered(code: Code): Any? = evalExSuccess(code).renderedValue

val EvalResultEx.rawValue get(): Any? {
    this.shouldBeTypeOf<EvalResultEx.Success>()
    return this.internalResult.result.value
}

val EvalResultEx.renderedValue get(): Any? {
    this.shouldBeTypeOf<EvalResultEx.Success>()
    return this.renderedValue
}

val EvalResultEx.displayValue get(): Any? {
    this.shouldBeTypeOf<EvalResultEx.Success>()
    return this.displayValue
}

fun EvalResultEx.assertSuccess() {
    when (this) {
        is EvalResultEx.AbstractError -> throw error
        is EvalResultEx.Interrupted -> throw InterruptedException()
        is EvalResultEx.Success -> {}
    }
}

val MimeTypedResult.text get() = this[MimeTypes.PLAIN_TEXT] as String

fun MutableDisplayResultWithCell.shouldBeText(): String {
    shouldBeInstanceOf<DisplayResultWrapper>()

    val display = display
    display.shouldBeInstanceOf<MimeTypedResult>()

    return display.text
}

fun Any?.shouldBeInstanceOf(kclass: KClass<*>) {
    if (this == null) {
        throw AssertionError("Expected instance of ${kclass.qualifiedName}, but got null")
    }
    if (!kclass.isInstance(this)) {
        throw AssertionError("Expected instance of ${kclass.qualifiedName}, but got ${this::class.qualifiedName}")
    }
}

/**
 * A factory interface for creating temporary files and directories on the filesystem.
 */
interface TempFilesFactory {
    /**
     * Creates a new empty temporary directory.
     * Clients shouldn't care about the directory cleanup,
     * it's the responsibility of the exact implementation.
     */
    fun newTempDir(): Path
}

/**
 * Allows creating temporary directories, that will be removed after
 * [action] is finished.
 * Directories will have the prefix [dirPrefix].
 */
@OptIn(ExperimentalPathApi::class)
fun withTempDirectories(
    dirPrefix: String,
    action: TempFilesFactory.() -> Unit,
) {
    val directories: MutableList<Path> = mutableListOf()

    try {
        val provider =
            object : TempFilesFactory {
                override fun newTempDir(): Path =
                    createTempDirectory(dirPrefix).also {
                        it.toFile().deleteOnExit()
                        directories.add(it)
                    }
            }
        action(provider)
    } finally {
        mergeExceptions {
            for (dir in directories) {
                catchIndependently {
                    dir.deleteRecursively()
                }
            }
        }
    }
}
