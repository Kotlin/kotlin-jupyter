package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

interface MultiGraph<out T> {
    val nodes: Set<GraphNode<T>>
    val directedEdges: Collection<DirectedEdge<T>>
    val undirectedEdges: Collection<UndirectedEdge<T>>

    companion object {
        fun <T> of(elements: Iterable<GraphNode<T>>): MultiGraph<T> {
            val nodes = mutableSetOf<GraphNode<T>>()
            val directedEdges = mutableListOf<DirectedEdge<T>>()
            val undirectedEdges = mutableListOf<UndirectedEdge<T>>()

            for (element in elements) element.populate(nodes, directedEdges, undirectedEdges)

            return MultiGraphImpl(nodes, directedEdges, undirectedEdges)
        }

        fun <T> of(vararg elements: GraphNode<T>): MultiGraph<T> = of(elements.toList())
    }
}
