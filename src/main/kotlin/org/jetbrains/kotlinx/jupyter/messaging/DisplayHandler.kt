package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

interface DisplayHandler {
    fun handleDisplay(value: Any, host: ExecutionHost, id: String? = null)
    fun handleUpdate(value: Any, host: ExecutionHost, id: String? = null)
}
