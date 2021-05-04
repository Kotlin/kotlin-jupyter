package org.jetbrains.kotlinx.jupyter.api.graphs

/**
 * [Label] contains all information related to the node itself
 */
interface Label {
    /**
     * Node text. May be simple simple text or HTML
     */
    val text: String

    /**
     * Shape of this node. The full list of shapes is given
     * [here](https://graphviz.org/doc/info/shapes.html)
     */
    val shape: String? get() = null
}
