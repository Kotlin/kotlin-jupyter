package org.jetbrains.kotlinx.jupyter.repl.notebook.impl

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.repl.notebook.DisplayResultWrapper
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableDisplayContainer
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableDisplayResultWithCell

class DisplayContainerImpl : MutableDisplayContainer {
    private val displays: MutableMap<String?, MutableList<DisplayResultWrapper>> = mutableMapOf()

    override fun add(display: DisplayResultWrapper) {
        synchronized(displays) {
            val list = displays.getOrPut(display.id) { mutableListOf() }
            list.add(display)
        }
    }

    override fun add(
        display: DisplayResult,
        cell: MutableCodeCell,
    ) {
        add(DisplayResultWrapper.create(display, cell))
    }

    override fun getAll(): List<MutableDisplayResultWithCell> {
        synchronized(displays) {
            return displays.flatMap { it.value }
        }
    }

    override fun getById(id: String?): List<MutableDisplayResultWithCell> {
        synchronized(displays) {
            return displays[id]?.toList().orEmpty()
        }
    }

    override fun update(
        id: String?,
        display: DisplayResult,
    ) {
        synchronized(displays) {
            val currentDisplays = displays[id] ?: return
            val updated =
                currentDisplays.map { currentDisplay ->
                    currentDisplay.display.dispose()
                    DisplayResultWrapper.create(display, currentDisplay.cell)
                }
            currentDisplays.clear()
            currentDisplays.addAll(updated)
        }
    }
}
