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
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.util.JupyterClientDetector

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
