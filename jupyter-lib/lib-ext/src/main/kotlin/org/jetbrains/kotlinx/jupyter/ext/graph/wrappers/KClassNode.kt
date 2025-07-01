package org.jetbrains.kotlinx.jupyter.ext.graph.wrappers

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.api.graphs.Label
import org.jetbrains.kotlinx.jupyter.api.graphs.NodeWrapper
import org.jetbrains.kotlinx.jupyter.api.graphs.labels.KClassLabel
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

class KClassNode(
    node: KClass<*>,
) : NodeWrapper<KClass<*>>(node) {
    override val label: Label get() = KClassLabel(value)

    override val inNodes by lazy {
        node.superclasses.map { KClassNode(it) }
    }
}

fun GraphNode.Companion.fromClass(kClass: KClass<*>) = KClassNode(kClass)

inline fun <reified T> GraphNode.Companion.fromClass() = fromClass(T::class)
