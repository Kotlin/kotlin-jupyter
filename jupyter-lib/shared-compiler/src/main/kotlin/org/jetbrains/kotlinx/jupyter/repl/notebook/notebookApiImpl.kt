package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell

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
    override var declarations: List<DeclarationInfo>
    override var preprocessedCode: String
    override var internalId: Int

    fun appendStreamOutput(output: String)

    fun addDisplay(display: DisplayResult)

    override val displays: MutableDisplayContainer
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
