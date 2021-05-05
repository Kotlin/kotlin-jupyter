package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

class GraphImpl<out T>(
    override val nodes: Set<GraphNode<T>>,
    override val directedEdges: Set<DirectedEdge<T>>,
    override val undirectedEdges: Set<UndirectedEdge<T>>,
) : Graph<T>
