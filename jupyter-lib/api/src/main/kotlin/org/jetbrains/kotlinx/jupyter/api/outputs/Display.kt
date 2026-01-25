package org.jetbrains.kotlinx.jupyter.api.outputs

import org.jetbrains.kotlinx.jupyter.api.Notebook

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
