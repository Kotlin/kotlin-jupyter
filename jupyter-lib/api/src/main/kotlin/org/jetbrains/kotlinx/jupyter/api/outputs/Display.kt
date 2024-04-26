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
