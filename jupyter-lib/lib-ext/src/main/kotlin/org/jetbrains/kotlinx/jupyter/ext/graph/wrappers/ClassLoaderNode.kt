package org.jetbrains.kotlinx.jupyter.ext.graph.wrappers

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.api.graphs.NodeWrapper
import org.jetbrains.kotlinx.jupyter.api.graphs.labels.TextLabel
import java.net.URLClassLoader
import kotlin.reflect.KClass

class ClassLoaderNode(node: ClassLoader) : NodeWrapper<ClassLoader>(node) {
    override val inNodes by lazy {
        node.parent?.let { listOf(ClassLoaderNode(it)) } ?: emptyList()
    }
    override val label = TextLabel(
        when (node) {
            is URLClassLoader -> node.urLs.joinToString("\\n", "URL ClassLoader:\\n") {
                it.toString()
            }
            else -> node.toString()
        }
    )
}

fun GraphNode.Companion.fromClassLoader(classLoader: ClassLoader) = ClassLoaderNode(classLoader)
fun GraphNode.Companion.fromClassLoader(kClass: KClass<*>) = fromClassLoader(kClass.java.classLoader)
inline fun <reified T> GraphNode.Companion.fromClassLoader() = fromClassLoader(T::class)
