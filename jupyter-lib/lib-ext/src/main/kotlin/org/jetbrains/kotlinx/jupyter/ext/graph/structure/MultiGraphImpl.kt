package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

class MultiGraphImpl<T>(
    override val nodes: Set<GraphNode<T>>,
    override val directedEdges: List<DirectedEdge<T>>,
    override val undirectedEdges: List<UndirectedEdge<T>>,
) : MultiGraph<T>
