package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.DisplayResult

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
