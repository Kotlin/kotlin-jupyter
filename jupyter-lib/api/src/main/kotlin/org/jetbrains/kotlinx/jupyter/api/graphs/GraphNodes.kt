package org.jetbrains.kotlinx.jupyter.api.graphs

/**
 * [GraphNodes] is a graph effectively and is rendered with `lib-ext` as well
 * as [GraphNode]
 */
data class GraphNodes<T>(
    val nodes: Iterable<GraphNode<T>>,
)
