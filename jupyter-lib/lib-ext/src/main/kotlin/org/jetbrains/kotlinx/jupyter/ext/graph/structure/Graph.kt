package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

interface Graph<out T> : MultiGraph<T> {
    override val directedEdges: Set<DirectedEdge<T>>
    override val undirectedEdges: Set<UndirectedEdge<T>>

    companion object {
        fun <T> of(elements: Iterable<GraphNode<T>>): Graph<T> {
            val nodes = mutableSetOf<GraphNode<T>>()
            val directedEdges = mutableSetOf<DirectedEdge<T>>()
            val undirectedEdges = mutableSetOf<UndirectedEdge<T>>()

            for (element in elements) element.populate(nodes, directedEdges, undirectedEdges)

            return GraphImpl(nodes, directedEdges, undirectedEdges)
        }

        fun <T> of(vararg elements: GraphNode<T>): Graph<T> = of(elements.toList())
    }
}
