package org.jetbrains.kotlinx.jupyter.api.outputs

import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.Notebook

/**
 * Renders the given value using the notebook's execution host rendering mechanism, if available.
 *
 * @param value The value to be rendered.
 * @return A [DisplayResult] representing the rendered output,
 * or `null` if rendering is not supported or if [value] is [Unit].
 * Note: if the [value] is null, the method may still return a non-null [DisplayResult].
 */
fun Notebook.render(value: Any?): DisplayResult? = executionHost?.render(value)

/**
 * Displays the given value in the notebook.
 *
 * @param value The value to display.
 * @param id An optional ID for the display.
 */
fun Notebook.display(
    value: Any,
    id: String? = null,
) {
    executionHost!!.display(value, id)
}

/**
 * Updates the display with the given value.
 *
 * @param value The value to update the display with.
 * @param id An optional identifier for the display to update.
 */
fun Notebook.updateDisplay(
    value: Any,
    id: String? = null,
) {
    executionHost!!.updateDisplay(value, id)
}

/**
 * Clears the output that is visible on the frontend.
 *
 * @param wait Wait to clear the output until a new output is available.
 * If true, it clears the existing output immediately before the new output is displayed.
 * Useful for creating simple animations with minimal flickering.
 */
fun Notebook.clearOutput(wait: Boolean = false) {
    executionHost!!.clearOutput(wait)
}
