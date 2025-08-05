package org.jetbrains.kotlinx.jupyter.api.graphs

import org.jetbrains.kotlinx.jupyter.api.graphs.labels.TextLabel

/**
 * Use [NodeWrapper] if [T] cannot implement [GraphNode] itself for some reason
 */
abstract class NodeWrapper<T>(
    val value: T,
) : GraphNode<T> {
    override val label: Label get() = TextLabel(value.toString())

    override val inNodes get() = listOf<GraphNode<T>>()
    override val outNodes get() = listOf<GraphNode<T>>()
    override val biNodes get() = listOf<GraphNode<T>>()

    override fun equals(other: Any?): Boolean = other is NodeWrapper<*> && other.value == this.value

    override fun hashCode(): Int = value.hashCode()
}
