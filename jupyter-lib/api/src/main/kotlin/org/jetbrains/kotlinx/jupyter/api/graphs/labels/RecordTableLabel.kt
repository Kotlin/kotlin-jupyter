package org.jetbrains.kotlinx.jupyter.api.graphs.labels

import org.jetbrains.kotlinx.jupyter.api.graphs.Label

/**
 * Renders as n-column table
 * First column consists of one cell containing [mainText].
 * Next `(n-1)` columns contain values from [properties]. It is
 * supposed that all element in [properties] collection would
 * have `(n-1)` elements.
 */
abstract class RecordTableLabel : Label {
    override val text: String get() {
        val nProperties = properties.size

        fun inTable(builderAction: StringBuilder.() -> Unit) = buildString {
            append("<<table>")
            builderAction()
            append("</table>>")
        }

        if (nProperties == 0) return inTable { append("<tr><td>$mainText</td></tr>") }
        return inTable {
            properties.forEachIndexed { i, prop ->
                append("<tr>")
                if (i == 0 && mainText != null) {
                    append("""<td rowspan="$nProperties">$mainText</td>""")
                }
                prop.forEach { value ->
                    append("""<td>$value</td>""")
                }
                appendLine("</tr>")
            }
        }
    }

    override val shape: String? get() = "plaintext"

    abstract val mainText: String?

    abstract val properties: Collection<Iterable<String>>
}
