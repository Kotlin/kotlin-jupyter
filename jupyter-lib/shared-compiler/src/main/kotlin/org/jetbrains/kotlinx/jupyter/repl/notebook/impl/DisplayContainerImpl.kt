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
            return displays[id]?.toList().orEmpty()
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
