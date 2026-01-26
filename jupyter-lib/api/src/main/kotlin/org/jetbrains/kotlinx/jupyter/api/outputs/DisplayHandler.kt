package org.jetbrains.kotlinx.jupyter.api.outputs

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

/**
 * The `DisplayHandler` interface defines methods for handling the display and update of values within the notebook.
 */
interface DisplayHandler {
    /**
     * Renders the [value] using the given [host]
     */
    fun render(
        value: Any?,
        host: ExecutionHost,
    ): DisplayResult? = null

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

    /**
     * Handles clearing the output
     *
     * @param wait Wait to clear the output until a new output is available.
     * If true, it clears the existing output immediately before the new output is displayed.
     * Useful for creating simple animations with minimal flickering.
     */
    fun handleClearOutput(wait: Boolean) {
    }
}
