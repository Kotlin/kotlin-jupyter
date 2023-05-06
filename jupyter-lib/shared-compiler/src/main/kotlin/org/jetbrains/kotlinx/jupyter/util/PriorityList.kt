package org.jetbrains.kotlinx.jupyter.util

import java.util.*

class PriorityList<T>(
    private val latterFirst: Boolean = true,
) : Iterable<T> {
    private val c = TreeSet<Entry<T>>()
    private var count = 0

    /**
     * Complexity: O(1)
     */
    val size: Int
        get() = c.size

    fun clear() {
        count = 0
        c.clear()
    }

    fun isEmpty() = c.isEmpty()

    /**
     * Collection iterator
     * Amortized next()/hasNext() complexity: O(1)
     */
    override fun iterator(): Iterator<T> {
        return MyIterator()
    }

    /**
     * Adds [value] to the list with specified [priority]
     * Complexity: O(log n)
     */
    fun add(value: T, priority: Int) {
        if (latterFirst) {
            ++count
        } else {
            --count
        }
        c.add(Entry(value, priority, count))
    }

    /**
     * Removes all values that are equal to [value]
     * Complexity: O(n + k * log(n)) where k is a number of elements to remove
     */
    fun remove(value: T) {
        removeIf { it == value }
    }

    fun removeIf(predicate: (T) -> Boolean) {
        val entriesForRemoval = c.filter { predicate(it.value) }
        for (entry in entriesForRemoval) {
            c.remove(entry)
        }
    }

    /**
     * All collection elements
     * Complexity: O(n)
     */
    fun elements(): Collection<T> {
        return c.map { it.value }
    }

    /**
     * If a [value] wasn't previously added to the list, simply adds it with a given [priority].
     * Otherwise, in case if [priority] is less or equal to the priority of existent element(s), does nothing.
     * Otherwise, removes all existing elements from the list and adds [value] with a given [priority].
     * Complexity: O(n + k * log(n)) where k is a number of elements equal to [value]
     */
    fun addOrUpdatePriority(value: T, priority: Int) {
        val maxPriority: Int? = c.filter { it.value == value }.maxByOrNull { it.priority }?.priority
        if (maxPriority != null) {
            if (maxPriority >= priority) {
                return
            } else {
                remove(value)
            }
        }
        add(value, priority)
    }

    /**
     * All elements with their priorities ordered by the time of adding
     * (elements added earlier go first)
     * Complexity: O(n log(n))
     */
    fun elementsWithPriority(): List<Pair<T, Int>> {
        return c.sortedBy { it.order }.map { it.value to it.priority }
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
