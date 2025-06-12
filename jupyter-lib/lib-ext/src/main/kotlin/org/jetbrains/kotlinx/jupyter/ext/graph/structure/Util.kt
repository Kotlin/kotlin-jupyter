package org.jetbrains.kotlinx.jupyter.ext.graph.structure

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode

val <T> GraphNode<T>.allParents: Iterable<GraphNode<T>> get() {
    return IterablesView(listOf(inNodes, outNodes, biNodes))
}

private class IterablesView<T>(
    private val iterables: Iterable<Iterable<T>>,
) : Iterable<T> {
    override fun iterator(): Iterator<T> = MyIterator(iterables)

    class MyIterator<T>(
        iterables: Iterable<Iterable<T>>,
    ) : Iterator<T> {
        private val outerIterator = iterables.iterator()
        private var innerIterator: Iterator<T>? = null

        override fun hasNext(): Boolean {
            while (innerIterator?.hasNext() != true) {
                if (!outerIterator.hasNext()) return false
                innerIterator = outerIterator.next().iterator()
            }
            return true
        }

        override fun next(): T {
            if (!hasNext()) throw IndexOutOfBoundsException()
            return innerIterator!!.next()
        }
    }
}

fun <T> GraphNode<T>.populate(
    nodes: MutableSet<GraphNode<T>>,
    directedEdges: MutableCollection<DirectedEdge<T>>,
    undirectedEdges: MutableCollection<UndirectedEdge<T>>,
) {
    nodes.add(this)
    for (parent in inNodes) {
        directedEdges.add(DirectedEdge(parent, this))
    }
    for (parent in outNodes) {
        directedEdges.add(DirectedEdge(this, parent))
    }
    for (parent in this.biNodes) {
        undirectedEdges.add(UndirectedEdge(this, parent))
    }
    for (parent in allParents) {
        if (parent !in nodes) parent.populate(nodes, directedEdges, undirectedEdges)
    }
}
