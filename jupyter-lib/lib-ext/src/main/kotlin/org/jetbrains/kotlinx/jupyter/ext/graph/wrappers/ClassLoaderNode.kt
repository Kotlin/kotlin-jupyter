package org.jetbrains.kotlinx.jupyter.ext.graph.wrappers

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.api.graphs.NodeWrapper
import org.jetbrains.kotlinx.jupyter.api.graphs.labels.TextLabel
import java.net.URLClassLoader
import kotlin.reflect.KClass

class ClassLoaderRenderingConfiguration(
    val withUrlDependencies: Boolean = true,
    val renderer: ClassLoaderNodeRenderer = DefaultClassLoaderNodeRenderer,
) {
    companion object {
        val DEFAULT = ClassLoaderRenderingConfiguration()
    }
}

typealias ClassLoaderNodeRenderer = ClassLoaderRenderingConfiguration.(ClassLoader) -> String

val DefaultClassLoaderNodeRenderer: ClassLoaderNodeRenderer = { node ->
    when {
        node is URLClassLoader && withUrlDependencies ->
            node.urLs.joinToString("\\n", "URL ClassLoader:\\n") {
                it.toString()
            }
        else -> node.toString()
    }
}

class ClassLoaderNode(
    node: ClassLoader,
    conf: ClassLoaderRenderingConfiguration,
) : NodeWrapper<ClassLoader>(node) {
    override val inNodes by lazy {
        node.parent?.let { listOf(ClassLoaderNode(it, conf)) } ?: emptyList()
    }
    override val label =
        TextLabel(
            conf.renderer(conf, node),
        )
}

fun GraphNode.Companion.fromClassLoader(
    classLoader: ClassLoader,
    conf: ClassLoaderRenderingConfiguration = ClassLoaderRenderingConfiguration.DEFAULT,
) = ClassLoaderNode(classLoader, conf)

fun GraphNode.Companion.fromClassLoader(
    kClass: KClass<*>,
    conf: ClassLoaderRenderingConfiguration = ClassLoaderRenderingConfiguration.DEFAULT,
) = fromClassLoader(kClass.java.classLoader, conf)

inline fun <reified T> GraphNode.Companion.fromClassLoader() = fromClassLoader(T::class)
