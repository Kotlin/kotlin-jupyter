package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

data class DirectedEdge<out T>(
    val fromNode: GraphNode<T>,
    val toNode: GraphNode<T>,
)
