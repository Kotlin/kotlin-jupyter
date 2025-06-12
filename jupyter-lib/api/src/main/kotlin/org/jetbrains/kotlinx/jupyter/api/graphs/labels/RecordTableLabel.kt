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

        fun inTable(builderAction: StringBuilder.() -> Unit) =
            buildString {
                append("<<table${attributes.html}>")
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
    open val attributes: TableAttributes get() = TableAttributes.default

    abstract val mainText: String?
    abstract val properties: Collection<Iterable<String>>

    class TableAttributes private constructor(
        properties: MutableMap<String, Any> = mutableMapOf(),
    ) : HtmlAttributes<TableAttributes>(properties) {
        var align by attr<String>()
        var bgcolor by attr<String>()
        var border by attr<Int>()
        var cellborder by attr<Int>()
        var cellpadding by attr<Int>()
        var cellspacing by attr<Int>()
        var color by attr<String>()
        var columns by attr<String>()
        var fixedsize by attr<Boolean>()
        var gradientangle by attr<Double>()
        var height by attr<Int>()
        var href by attr<String>()
        var id by attr<String>()
        var port by attr<String>()
        var rows by attr<String>()
        var sides by attr<String>()
        var style by attr<String>()
        var target by attr<String>()
        var title by attr<String>()
        var tooltip by attr<String>()
        var valign by attr<String>()
        var width by attr<Int>()

        override fun copy(): TableAttributes = TableAttributes(properties)

        companion object : HtmlAttributesCompanion<TableAttributes>() {
            override val default = TableAttributes()
        }
    }
}
