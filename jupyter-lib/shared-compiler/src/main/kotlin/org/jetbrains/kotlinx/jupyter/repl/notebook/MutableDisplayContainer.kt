package org.jetbrains.kotlinx.jupyter.repl.notebook

import org.jetbrains.kotlinx.jupyter.api.DisplayContainer
import org.jetbrains.kotlinx.jupyter.api.DisplayResult

interface MutableDisplayContainer : DisplayContainer {
    fun add(display: DisplayResultWrapper)

    fun add(display: DisplayResult, cell: MutableCodeCell)

    fun update(id: String?, display: DisplayResult)

    override fun getAll(): List<MutableDisplayResultWithCell>

    override fun getById(id: String?): List<MutableDisplayResultWithCell>
}
