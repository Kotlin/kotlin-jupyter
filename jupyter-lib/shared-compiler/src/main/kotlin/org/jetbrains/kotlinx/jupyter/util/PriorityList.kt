package org.jetbrains.kotlinx.jupyter.util

import java.util.*

class PriorityList<T> : Iterable<T> {
    private val c = TreeSet<Entry<T>>()
    private var count = -1

    val size: Int
        get() = c.size

    fun clear() = c.clear()

    fun isEmpty() = c.isEmpty()

    override fun iterator(): Iterator<T> {
        return MyIterator()
    }

    fun add(value: T, priority: Int) {
        ++count
        c.add(Entry(value, priority, count))
    }

    private class Entry<T>(
        val value: T,
        val priority: Int,
        val order: Int,
    ) : Comparable<Entry<T>> {
        override fun compareTo(other: Entry<T>): Int {
            val cp = other.priority.compareTo(priority)
            if (cp != 0) return cp
            return other.order.compareTo(order)
        }
    }

    private inner class MyIterator : Iterator<T> {
        private val mapIter = c.iterator()

        override fun hasNext(): Boolean {
            return mapIter.hasNext()
        }

        override fun next(): T {
            return mapIter.next().value
        }
    }
}
