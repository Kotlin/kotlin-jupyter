package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

data class UndirectedEdge<out T>(
    val fromNode: GraphNode<T>,
    val toNode: GraphNode<T>,
) {
    override fun equals(other: Any?): Boolean =
        other is UndirectedEdge<*> &&
            (
                (fromNode == other.fromNode) &&
                    (toNode == other.toNode) ||
                    (fromNode == other.toNode) &&
                    (toNode == other.fromNode)
            )

    override fun hashCode(): Int {
        var h1 = fromNode.hashCode()
        var h2 = toNode.hashCode()
        if (h1 > h2) {
            val t = h2
            h2 = h1
            h1 = t
        }
        return 31 * h1 + h2
    }
}
