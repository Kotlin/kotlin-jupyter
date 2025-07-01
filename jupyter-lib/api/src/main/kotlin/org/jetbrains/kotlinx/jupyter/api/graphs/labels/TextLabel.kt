package org.jetbrains.kotlinx.jupyter.api.graphs.labels

import org.jetbrains.kotlinx.jupyter.api.graphs.Label

/**
 * Label representing a plain text inside a given [shape]
 */
class TextLabel(
    value: String,
    override val shape: String? = "ellipse",
) : Label {
    override val text: String = "\"${value.replace("\"", "\\\"")}\""
}
