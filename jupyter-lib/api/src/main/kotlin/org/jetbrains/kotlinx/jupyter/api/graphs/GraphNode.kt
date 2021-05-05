package org.jetbrains.kotlinx.jupyter.api.graphs

/**
 * Graph node which represents the object as a part of some hierarchy
 *
 * Classes implementing this interface should take care of [equals] and [hashCode]
 * because they are used for testing the nodes for equality, and wrong implementation
 * of these methods may lead to the wrong graph rendering, StackOverflow / OutOfMemory
 * errors and so on. See example in [NodeWrapper]
 *
 * @param T Underlying object type
 */
interface GraphNode<out T> {
    /**
     * Node label with all required information
     */
    val label: Label

    /**
     * Nodes which are connected with the ingoing edges to this one:
     * {this} <- {inNode}
     */
    val inNodes: List<GraphNode<T>>

    /**
     * Nodes which are connected with the outgoing edges to this one:
     * {this} -> {outNode}
     */
    val outNodes: List<GraphNode<T>>

    /**
     * Nodes which are connected with the undirected edges to this one:
     * {this} -- {biNode}
     */
    val biNodes: List<GraphNode<T>>

    companion object
}
