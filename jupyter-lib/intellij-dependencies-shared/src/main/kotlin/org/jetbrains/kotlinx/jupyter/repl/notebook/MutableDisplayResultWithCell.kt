package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.DisplayResultWithCell

interface MutableDisplayResultWithCell : DisplayResultWithCell {
    override val cell: MutableCodeCell
}
