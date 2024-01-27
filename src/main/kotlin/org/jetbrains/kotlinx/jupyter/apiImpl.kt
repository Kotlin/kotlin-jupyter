package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.HtmlData
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.LibraryLoader
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
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
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.repl.EvalData
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.SharedReplContext
import org.jetbrains.kotlinx.jupyter.repl.notebook.DisplayResultWrapper
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableDisplayContainer
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableDisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import kotlin.properties.Delegates

class DisplayContainerImpl : MutableDisplayContainer {
    private val displays: MutableMap<String?, MutableList<DisplayResultWrapper>> = mutableMapOf()

    override fun add(display: DisplayResultWrapper) {
        synchronized(displays) {
            val list = displays.getOrPut(display.id) { mutableListOf() }
            list.add(display)
        }
    }

    override fun add(display: DisplayResult, cell: MutableCodeCell) {
        add(DisplayResultWrapper.create(display, cell))
    }

    override fun getAll(): List<MutableDisplayResultWithCell> {
        synchronized(displays) {
            return displays.flatMap { it.value }
        }
    }

    override fun getById(id: String?): List<MutableDisplayResultWithCell> {
        synchronized(displays) {
            return displays[id]?.toList().orEmpty()
        }
    }

    override fun update(id: String?, display: DisplayResult) {
        synchronized(displays) {
            val initialDisplays = displays[id] ?: return
            val updated = initialDisplays.map { DisplayResultWrapper.create(display, it.cell) }
            initialDisplays.clear()
            initialDisplays.addAll(updated)
        }
    }
}

class CodeCellImpl(
    override val notebook: NotebookImpl,
    override val id: Int,
    override val code: String,
    override val prevCell: CodeCell?,
) : MutableCodeCell {
    override var resultVal: Any? = null
    override var internalId by Delegates.notNull<Int>()

    override var declarations: List<DeclarationInfo> = emptyList()
    override var preprocessedCode by Delegates.notNull<String>()

    override val result: Any?
        get() = resultVal

    private var isStreamOutputUpToDate: Boolean = true
    private var collectedStreamOutput: String = ""
    private val streamBuilder = StringBuilder()
    override fun appendStreamOutput(output: String) {
        isStreamOutputUpToDate = false
        streamBuilder.append(output)
    }

    override val streamOutput: String
        get() {
            if (!isStreamOutputUpToDate) {
                isStreamOutputUpToDate = true
                collectedStreamOutput = streamBuilder.toString()
            }

            return collectedStreamOutput
        }

    override val displays = DisplayContainerImpl()

    override fun addDisplay(display: DisplayResult) {
        val wrapper = DisplayResultWrapper.create(display, this)
        displays.add(wrapper)
        notebook.displays.add(wrapper)
    }
}

class NotebookImpl(
    private val runtimeProperties: ReplRuntimeProperties,
    override val commManager: CommManager,
    private val explicitClientType: JupyterClientType?,
    override val libraryLoader: LibraryLoader,
) : MutableNotebook {
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

    override val resultsAccessor = ResultsAccessor { getResult(it) }

    override fun getCell(id: Int): MutableCodeCell {
        return cells[id] ?: throw ArrayIndexOutOfBoundsException(
            "There is no cell with number '$id'",
        )
    }

    override fun getResult(id: Int): Any? {
        return getCell(id).result
    }

    private val history = arrayListOf<MutableCodeCell>()
    private var mainCellCreated = false

    override val displays = DisplayContainerImpl()

    override fun getAllDisplays(): List<DisplayResultWithCell> {
        return displays.getAll()
    }

    override fun getDisplaysById(id: String?): List<DisplayResultWithCell> {
        return displays.getById(id)
    }

    override val kernelVersion: KotlinKernelVersion
        get() = runtimeProperties.version ?: throw IllegalStateException("Kernel version is not known")
    override val jreInfo: JREInfoProvider
        get() = JavaRuntime

    override val jupyterClientType: JupyterClientType by lazy {
        explicitClientType ?: JupyterClientDetector.detect()
    }

    override fun addCell(
        data: EvalData,
    ): MutableCodeCell {
        val cell = CodeCellImpl(this, data.executionCounter, data.rawCode, lastCell)
        cells[data.executionCounter] = cell
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

    override fun renderHtmlAsIFrame(data: HtmlData): MimeTypedResult {
        return data.toIFrame(_currentColorScheme)
    }

    override val currentCell: MutableCodeCell?
        get() = history(0)

    override val lastCell: MutableCodeCell?
        get() = history(1)

    override val renderersProcessor: ResultsRenderersProcessor
        get() = sharedReplContext?.renderersProcessor ?: throwItemNotInitialized("Type renderers processor")

    override val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion
        get() = sharedReplContext?.textRenderersProcessor ?: throwItemNotInitialized("Text renderers processor")

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

    private fun throwItemNotInitialized(itemName: String): Nothing {
        throw IllegalStateException("$itemName is not initialized yet")
    }

    override val libraryRequests: Collection<LibraryResolutionRequest>
        get() = sharedReplContext?.librariesProcessor?.requests.orEmpty()

    override fun getLibraryFromDescriptor(descriptorText: String, options: Map<String, String>): LibraryDefinition {
        return parseLibraryDescriptor(descriptorText)
            .convertToDefinition(options.entries.map { Variable(it.key, it.value) })
    }
}
