package org.jetbrains.kotlinx.jupyter.test.display

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.withIdIfNotNull
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.renderValue

abstract class AbstractTestDisplayHandler : DisplayHandler {
    override fun render(
        value: Any?,
        host: ExecutionHost,
    ): DisplayResult? = null

    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
    }

    override fun handleClearOutput(wait: Boolean) {
    }
}

open class TestDisplayHandler(
    val list: MutableList<Any> = mutableListOf(),
) : AbstractTestDisplayHandler() {
    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        list.add(value)
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        TODO("Not yet implemented")
    }
}

open class TestDisplayHandlerWithRendering(
    private val notebook: MutableNotebook,
) : AbstractTestDisplayHandler() {
    override fun render(
        value: Any?,
        host: ExecutionHost,
    ) = renderValue(notebook, host, value)

    override fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = render(value, host)?.withIdIfNotNull(id) ?: return
        notebook.currentCell?.addDisplay(display)
    }

    override fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String?,
    ) {
        val display = render(value, host) ?: return
        val container = notebook.displays
        container.update(id, display)
        container.getById(id).distinctBy { it.cell.id }.forEach {
            it.cell.displays.update(id, display)
        }
    }
}
