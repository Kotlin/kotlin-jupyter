package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DisplayResult

interface MutableCodeCell : CodeCell {
    var resultVal: Any?
    override var declarations: List<DeclarationInfo>
    override var preprocessedCode: String
    override var internalId: Int

    fun appendStreamOutput(output: String)

    fun addDisplay(display: DisplayResult)

    override val displays: MutableDisplayContainer
}
