package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.ResultsAccessor
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.repl.impl.SharedReplContext

interface MutableDisplayResultWithCell : DisplayResultWithCell {
    override val cell: MutableCodeCell
}

interface MutableDisplayContainer : DisplayContainer {
    fun add(display: DisplayResultWrapper)

    fun add(display: DisplayResult, cell: MutableCodeCell)

    fun update(id: String?, display: DisplayResult)

    override fun getAll(): List<MutableDisplayResultWithCell>

    override fun getById(id: String?): List<MutableDisplayResultWithCell>
}

interface MutableCodeCell : CodeCell {
    var resultVal: Any?
    fun appendStreamOutput(output: String)

    fun addDisplay(display: DisplayResult)

    override val displays: MutableDisplayContainer
}

interface MutableNotebook : Notebook {
    var sharedReplContext: SharedReplContext?

    override val displays: MutableDisplayContainer
    fun addCell(
        internalId: Int,
        preprocessedCode: String,
        data: EvalData,
    ): MutableCodeCell

    fun beginEvalSession()

    override val currentCell: MutableCodeCell?
}

class DisplayResultWrapper private constructor(
    val display: DisplayResult,
    override val cell: MutableCodeCell,
) : DisplayResult by display, MutableDisplayResultWithCell {
    companion object {
        fun create(display: DisplayResult, cell: MutableCodeCell): DisplayResultWrapper {
            return if (display is DisplayResultWrapper) DisplayResultWrapper(display.display, cell)
            else DisplayResultWrapper(display, cell)
        }
    }
}

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
            return displays[id].orEmpty()
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
    override val internalId: Int,
    override val code: String,
    override val preprocessedCode: String,
    override val prevCell: CodeCell?,
) : MutableCodeCell {
    override var resultVal: Any? = null
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

class EvalData(
    val executionCounter: Int,
    val rawCode: String,
) {
    constructor(evalRequestData: EvalRequestData) : this(evalRequestData.jupyterId, evalRequestData.code)
}

class NotebookImpl(
    private val runtimeProperties: ReplRuntimeProperties,
    override val connection: JupyterConnection,
    override val commManager: CommManager,
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
            "There is no cell with number '$id'"
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
        JupyterClientDetector.detect()
    }

    override fun addCell(
        internalId: Int,
        preprocessedCode: String,
        data: EvalData,
    ): MutableCodeCell {
        val cell = CodeCellImpl(this, data.executionCounter, internalId, data.rawCode, preprocessedCode, lastCell)
        cells[data.executionCounter] = cell
        history.add(cell)
        mainCellCreated = true
        return cell
    }

    override fun beginEvalSession() {
        mainCellCreated = false
    }

    override fun history(before: Int): MutableCodeCell? {
        val offset = if (mainCellCreated) 1 else 0
        return history.getOrNull(history.size - offset - before)
    }

    override fun changeColorScheme(newScheme: ColorScheme) {
        val context = sharedReplContext ?: return
        context.colorSchemeChangeCallbacksProcessor.schemeChanged(newScheme)
    }

    override val currentCell: MutableCodeCell?
        get() = history(0)

    override val lastCell: MutableCodeCell?
        get() = history(1)

    override val renderersProcessor: RenderersProcessor
        get() = sharedReplContext?.renderersProcessor ?: throw IllegalStateException("Type renderers processor is not initialized yet")

    override val libraryRequests: Collection<LibraryResolutionRequest>
        get() = sharedReplContext?.librariesProcessor?.requests.orEmpty()
}
