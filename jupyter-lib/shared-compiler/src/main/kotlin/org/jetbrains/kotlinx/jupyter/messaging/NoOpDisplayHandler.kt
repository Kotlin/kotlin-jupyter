package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler

object NoOpDisplayHandler : DisplayHandler {
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
