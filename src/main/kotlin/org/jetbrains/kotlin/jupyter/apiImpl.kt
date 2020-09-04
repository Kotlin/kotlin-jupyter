package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlin.jupyter.api.CodeCell
import org.jetbrains.kotlin.jupyter.api.DisplayContainer
import org.jetbrains.kotlin.jupyter.api.DisplayResult
import org.jetbrains.kotlin.jupyter.api.DisplayResultWithCell
import org.jetbrains.kotlin.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.api.Notebook
import org.jetbrains.kotlin.jupyter.api.ResultsAccessor
import org.jetbrains.kotlin.jupyter.api.RuntimeUtils
import java.lang.IllegalStateException

class DisplayResultWrapper private constructor(
        val display: DisplayResult,
        override val cell: CodeCellImpl,
): DisplayResult by display, DisplayResultWithCell {
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
        override val host: KotlinKernelHost,
        private val runtimeProperties: ReplRuntimeProperties,
): Notebook<CodeCellImpl> {
    override val cells = hashMapOf<Int, CodeCellImpl>()
    override val results = object : ResultsAccessor {
        override fun get(i: Int): Any? {
            val cell = cells[i] ?: throw ArrayIndexOutOfBoundsException(
                    "There is no cell with number '$i'"
            )
            return cell.result
        }
    }
    private val history = arrayListOf<CodeCellImpl>()
    private var mainCellCreated = false

    override val displays = DisplayContainerImpl()

    override val kernelVersion: KotlinKernelVersion
        get() = runtimeProperties.version ?: throw IllegalStateException("Kernel version is not known")
    override val runtimeUtils: RuntimeUtils
        get() = JavaRuntime

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

    override fun prevCell(before: Int): CodeCellImpl? {
        val offset = if (mainCellCreated) 1 else 0
        return history.getOrNull(history.size - offset - before)
    }
}
