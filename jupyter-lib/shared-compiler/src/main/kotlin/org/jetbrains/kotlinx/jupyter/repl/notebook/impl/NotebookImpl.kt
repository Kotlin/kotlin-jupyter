package org.jetbrains.kotlinx.jupyter.repl.notebook.impl

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.HtmlData
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.LibraryLoader
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessorInternal
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TextRenderersProcessorWithPreventingRecursion
import org.jetbrains.kotlinx.jupyter.debug.DEBUG_THREAD_NAME
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.getInput
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.EvalData
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.SharedReplContext
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.util.JupyterClientDetector
import java.io.Closeable
import java.nio.file.Path
import kotlin.concurrent.thread

class NotebookImpl(
    override val loggerFactory: KernelLoggerFactory,
    private val runtimeProperties: ReplRuntimeProperties,
    override val commManager: CommManager,
    private val communicationFacility: JupyterCommunicationFacility,
    private val explicitClientType: JupyterClientType?,
    override val libraryLoader: LibraryLoader,
    override val kernelRunMode: KernelRunMode,
    debugPortProvided: Boolean,
) : MutableNotebook,
    Closeable {
    @Suppress("FunctionName")
    private fun `$debugMethod`() {
        try {
            while (true) {
                Thread.sleep(1000)
            }
        } catch (_: InterruptedException) {
        }
    }

    private val debugThread =
        if (debugPortProvided) {
            thread(
                name = DEBUG_THREAD_NAME,
                block = ::`$debugMethod`,
            )
        } else {
            null
        }
    private val cells = hashMapOf<Int, MutableCodeCell>()
    override var sharedReplContext: SharedReplContext? = null

    override val cellsList: Collection<MutableCodeCell>
        get() = cells.values

    override val variablesState: Map<String, VariableState> get() {
        return sharedReplContext?.evaluator?.variablesHolder
            ?: throw IllegalStateException("Evaluator is not initialized yet")
    }

    override val cellVariables: Map<Int, Set<String>> get() {
        return sharedReplContext?.evaluator?.cellVariables
            ?: throw IllegalStateException("Evaluator is not initialized yet")
    }

    override val currentClasspath: List<String>
        get() = sharedReplContext?.currentClasspathProvider?.provideClasspath() ?: emptyList()

    override val sessionOptions: SessionOptions get() {
        return sharedReplContext?.sessionOptions
            ?: throw IllegalStateException("Session options are not initialized yet")
    }

    override val resultsAccessor = ResultsAccessor { getResult(it) }

    override fun getCell(id: Int): MutableCodeCell =
        cells[id] ?: throw ArrayIndexOutOfBoundsException(
            "There is no cell with number '$id'",
        )

    override fun getResult(id: Int): Any? = getCell(id).result

    private val history = arrayListOf<MutableCodeCell>()
    private var mainCellCreated = false

    override val displays = DisplayContainerImpl()

    override fun getAllDisplays(): List<DisplayResultWithCell> = displays.getAll()

    override fun getDisplaysById(id: String?): List<DisplayResultWithCell> = displays.getById(id)

    override val kernelVersion: KotlinKernelVersion
        get() = runtimeProperties.version ?: throw IllegalStateException("Kernel version is not known")
    override val jreInfo: JREInfoProvider
        get() = JavaRuntime

    private val clientDetector by lazy {
        JupyterClientDetector(loggerFactory)
    }

    override val jupyterClientType: JupyterClientType by lazy {
        explicitClientType ?: clientDetector.detect()
    }

    override fun addCell(data: EvalData): MutableCodeCell {
        val cellId = data.executionCount.value
        val cell = CodeCellImpl(this, cellId, data.rawCode, lastCell)
        cells[cellId] = cell
        history.add(cell)
        mainCellCreated = true
        return cell
    }

    // Throws if no cell was added
    override fun popCell() {
        val lastCell = history.last()
        cells.remove(lastCell.id)
        history.removeLast()
    }

    override fun beginEvalSession() {
        mainCellCreated = false
    }

    override fun history(before: Int): MutableCodeCell? {
        val offset = if (mainCellCreated) 1 else 0
        return history.getOrNull(history.size - offset - before)
    }

    private var _currentColorScheme: ColorScheme = ColorScheme.LIGHT
    override val currentColorScheme: ColorScheme get() = _currentColorScheme

    override fun changeColorScheme(newScheme: ColorScheme) {
        _currentColorScheme = newScheme
        val context = sharedReplContext ?: return
        context.colorSchemeChangeCallbacksProcessor.schemeChanged(newScheme)
    }

    override fun renderHtmlAsIFrame(data: HtmlData): MimeTypedResult = data.toIFrame(_currentColorScheme)

    // This path is not set until a KernelRequestInfo message has been sent.
    // For normal usage, this should always happen, but it is not always
    // true inside the kernel (like in tests). If no path is set, the empty
    // path is returned when calling `notebook.workingDir`. While this has
    // different semantics (i.e., JVM home.dir), it should be good enough to
    // avoid leaking nullable paths and filenames to the user API.
    private var absoluteNotebookFilePath: Path? = null

    override fun updateFilePath(absoluteNotebookFilePath: Path) {
        this.absoluteNotebookFilePath = absoluteNotebookFilePath
    }

    override val workingDir: Path
        get() {
            return absoluteNotebookFilePath?.let {
                it.parent ?: Path.of("")
            } ?: Path.of("")
        }

    override val currentCell: MutableCodeCell?
        get() = history(0)

    override var intermediateClassLoader: ClassLoader? = null

    override val lastCell: MutableCodeCell?
        get() = history(1)

    override val renderersProcessor: ResultsRenderersProcessor
        get() = sharedReplContext?.renderersProcessor ?: throwItemNotInitialized("Type renderers processor")

    override val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion
        get() = sharedReplContext?.textRenderersProcessor ?: throwItemNotInitialized("Text renderers processor")

    override val throwableRenderersProcessor: ThrowableRenderersProcessor
        get() = sharedReplContext?.throwableRenderersProcessor ?: throwItemNotInitialized("Throwable renderers processor")

    override val fieldsHandlersProcessor: FieldsProcessorInternal
        get() = sharedReplContext?.fieldsProcessor ?: throwItemNotInitialized("Fields handlers processor")

    override val beforeCellExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
        get() = sharedReplContext?.beforeCellExecutionsProcessor ?: throwItemNotInitialized("Before-cell executions processor")

    override val afterCellExecutionsProcessor: ExtensionsProcessor<AfterCellExecutionCallback>
        get() = sharedReplContext?.afterCellExecutionsProcessor ?: throwItemNotInitialized("After-cell executions processor")

    override val shutdownExecutionsProcessor: ExtensionsProcessor<ExecutionCallback<*>>
        get() = sharedReplContext?.shutdownExecutionsProcessor ?: throwItemNotInitialized("Shutdown executions processor")

    override val codePreprocessorsProcessor: ExtensionsProcessor<CodePreprocessor>
        get() = sharedReplContext?.codePreprocessor ?: throwItemNotInitialized("Code preprocessors processor")

    override val interruptionCallbacksProcessor: ExtensionsProcessor<InterruptionCallback>
        get() = sharedReplContext?.interruptionCallbacksProcessor ?: throwItemNotInitialized("Interruptions callback processor")

    override val colorSchemeChangeCallbacksProcessor: ExtensionsProcessor<ColorSchemeChangedCallback>
        get() = sharedReplContext?.colorSchemeChangeCallbacksProcessor ?: throwItemNotInitialized("Color scheme change callbacks processor")

    private fun throwItemNotInitialized(itemName: String): Nothing = throw IllegalStateException("$itemName is not initialized yet")

    override val libraryRequests: Collection<LibraryResolutionRequest>
        get() = sharedReplContext?.librariesProcessor?.requests.orEmpty()

    override fun getLibraryFromDescriptor(
        descriptorText: String,
        options: Map<String, String>,
    ): LibraryDefinition =
        parseLibraryDescriptor(descriptorText)
            .convertToDefinition(options.entries.map { Variable(it.key, it.value) })

    override fun prompt(
        prompt: String,
        isPassword: Boolean,
    ): String = communicationFacility.getInput(prompt, isPassword)

    override var executionHost: KotlinKernelHost? = null

    override fun close() {
        debugThread?.interrupt()
    }
}
