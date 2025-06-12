package org.jetbrains.kotlinx.jupyter.ext.graph.visualization

import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.parse.Parser
import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.ext.Image
import org.jetbrains.kotlinx.jupyter.ext.graph.structure.MultiGraph
import java.io.ByteArrayOutputStream

fun <T> MultiGraph<T>.dotText(): String {
    val nodesNumbers = nodes.mapIndexed { index, hierarchyElement -> hierarchyElement to index }.toMap()

    fun id(el: GraphNode<T>) = "n${nodesNumbers[el]}"

    return buildString {
        appendLine("""digraph "" { """)
        for (node in nodes) {
            val nodeId = id(node)
            appendLine("$nodeId ;")
            append("$nodeId [")
            with(node.label) {
                append("label=$text ")
                shape?.let { append("shape=$it ") }
            }
            appendLine("] ;")
        }

        for ((n1, n2) in directedEdges) {
            appendLine("${id(n1)} -> ${id(n2)} ;")
        }
        for ((n1, n2) in undirectedEdges) {
            appendLine("${id(n1)} -> ${id(n2)} [dir=none] ;")
        }
        appendLine("}")
    }
}

fun renderDotText(text: String): Image {
    val graph = Parser().read(text)
    val stream = ByteArrayOutputStream()
    Graphviz
        .fromGraph(graph)
        .engine(Engine.DOT)
        .render(Format.SVG)
        .toOutputStream(stream)
    return Image(stream.toByteArray(), "svg")
}

fun <T> MultiGraph<T>.render(): Image = renderDotText(dotText())

fun <T> MultiGraph<T>.toHTML() = render().toHTML()
