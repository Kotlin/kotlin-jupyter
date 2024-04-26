package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler

object NoOpDisplayHandler : DisplayHandler {
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
}
