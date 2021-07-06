package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.VariableState

class DisplayResultWrapper private constructor(
    val display: DisplayResult,
    override val cell: CodeCellImpl,
) : DisplayResult by display, DisplayResultWithCell {
    companion object {
        fun create(display: DisplayResult, cell: CodeCellImpl): DisplayResultWrapper {
            return if (display is DisplayResultWrapper) DisplayResultWrapper(display.display, cell)
            else DisplayResultWrapper(display, cell)
        }
    }
}

class DisplayContainerImpl : DisplayContainer {
    private val displays: MutableMap<String?, MutableList<DisplayResultWrapper>> = mutableMapOf()

    fun add(display: DisplayResultWrapper) {
        val list = displays.getOrPut(display.id) { mutableListOf() }
        list.add(display)
    }

    fun add(display: DisplayResult, cell: CodeCellImpl) {
        add(DisplayResultWrapper.create(display, cell))
    }

    override fun getAll(): List<DisplayResultWithCell> {
        return displays.flatMap { it.value }
    }

    override fun getById(id: String?): List<DisplayResultWithCell> {
        return displays[id].orEmpty()
    }

    fun update(id: String?, display: DisplayResult) {
        val initialDisplays = displays[id] ?: return
        val updated = initialDisplays.map { DisplayResultWrapper.create(display, it.cell) }
        initialDisplays.clear()
        initialDisplays.addAll(updated)
    }
}

class CodeCellImpl(
    override val notebook: NotebookImpl,
    override val id: Int,
    override val internalId: Int,
    override val code: String,
    override val preprocessedCode: String,
    override val prevCell: CodeCell?,
) : CodeCell {
    var resultVal: Any? = null
    override val result: Any?
        get() = resultVal

    private var isStreamOutputUpToDate: Boolean = true
    private var collectedStreamOutput: String = ""
    private val streamBuilder = StringBuilder()
    fun appendStreamOutput(output: String) {
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

    fun addDisplay(display: DisplayResult) {
        val wrapper = DisplayResultWrapper.create(display, this)
        displays.add(wrapper)
        notebook.displays.add(wrapper)
    }
}

class EvalData(
    val executionCounter: Int,
    val rawCode: String,
)

class NotebookImpl(
    private val runtimeProperties: ReplRuntimeProperties,
) : Notebook {
    private val cells = hashMapOf<Int, CodeCellImpl>()
    internal var typeRenderersProcessor: RenderersProcessor? = null

    override val cellsList: Collection<CodeCellImpl>
        get() = cells.values

    override val variablesMap = mutableMapOf<String, VariableState>()

    override fun getCell(id: Int): CodeCellImpl {
        return cells[id] ?: throw ArrayIndexOutOfBoundsException(
            "There is no cell with number '$id'"
        )
    }

    override fun getResult(id: Int): Any? {
        return getCell(id).result
    }

    private val history = arrayListOf<CodeCellImpl>()
    private var mainCellCreated = false

    val displays = DisplayContainerImpl()

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

    fun updateVarsState(varsMap: Map<String, VariableState>) {
        variablesMap += varsMap
    }

    fun varsAsHtmlTable(): String {
        return generateHTMLVarsReport(variablesMap)
    }

    fun varsAsString(): String {
        if (variablesMap.isEmpty()) return ""
        val stringBuilder = StringBuilder("Visible vars: \n")
        variablesMap.forEach { (t, u) ->
            stringBuilder.append("\t$t : ${u.stringValue}\n")
        }
        return stringBuilder.append('\n').toString()
    }

    fun addCell(
        internalId: Int,
        preprocessedCode: String,
        data: EvalData,
    ): CodeCellImpl {
        val cell = CodeCellImpl(this, data.executionCounter, internalId, data.rawCode, preprocessedCode, lastCell)
        cells[data.executionCounter] = cell
        history.add(cell)
        mainCellCreated = true
        return cell
    }

    fun beginEvalSession() {
        mainCellCreated = false
    }

    override fun history(before: Int): CodeCellImpl? {
        val offset = if (mainCellCreated) 1 else 0
        return history.getOrNull(history.size - offset - before)
    }

    override val currentCell: CodeCellImpl?
        get() = history(0)

    override val lastCell: CodeCellImpl?
        get() = history(1)

    override val renderersProcessor: RenderersProcessor
        get() = typeRenderersProcessor ?: throw IllegalStateException("Type renderers processor is not initialized yet")
}
