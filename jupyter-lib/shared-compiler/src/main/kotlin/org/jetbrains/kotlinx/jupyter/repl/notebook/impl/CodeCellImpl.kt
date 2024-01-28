package org.jetbrains.kotlinx.jupyter.repl.notebook.impl

import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.repl.notebook.DisplayResultWrapper
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import kotlin.properties.Delegates

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
