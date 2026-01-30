package org.jetbrains.kotlinx.jupyter.api.outputs

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost

/**
 * The `DisplayHandler` interface defines methods for handling the display and update of values within the notebook.
 */
interface DisplayHandler {
    /**
     * Renders the [value] using the given [host].
     *
     * @param value The value to be rendered.
     * @param host The execution host
     * @return A [DisplayResult] representing the rendered output,
     * or `null` if rendering is not supported or if [value] is [Unit].
     * Note: if the [value] is null, the method may still return a non-null [DisplayResult].
     */
    fun render(
        value: Any?,
        host: ExecutionHost,
    ): DisplayResult?

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
    fun handleClearOutput(wait: Boolean)
}
