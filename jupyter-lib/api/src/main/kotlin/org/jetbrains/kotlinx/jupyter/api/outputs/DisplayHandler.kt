package org.jetbrains.kotlinx.jupyter.api.outputs

import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

/**
 * The `DisplayHandler` interface defines methods for handling the display and update of values within the notebook.
 */
interface DisplayHandler {
    /**
     * Handles the display of a value
     */
    fun handleDisplay(
        value: Any,
        host: ExecutionHost,
        id: String? = null,
    )

    /**
     * Handles the update of a value
     */
    fun handleUpdate(
        value: Any,
        host: ExecutionHost,
        id: String? = null,
    )
}
