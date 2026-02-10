package org.jetbrains.kotlinx.jupyter.streams

/**
 * Represents a list of data items.
 * Supports three operations:
 * - [add]: Adds data to the end of the list
 * - [remove]: Removes the given item from the list, returns
 *              *current* last element and indication if the
 *              removed element was last.
 * - [last]: Returns last element in the list, or [initialData] if the list is empty.
 *
 * This structure is NOT thread-safe.
 * Synchronization should be performed externally.
 *
 * @param DataT The type of data in the sequence.
 * @property initialData The initial data item in the sequence.
 * @constructor Creates a [DataList] with the initial data item.
 */
class DataList<DataT>(
    private val initialData: DataT,
) {
    private val storage = ArrayList<DataT>()

    /**
     * Adds [item] to the end of the list
     */
    fun add(item: DataT) {
        storage.add(item)
    }

    /**
     * Returns the last data item in the list.
     * If the list is empty, it returns the [initialData].
     */
    fun last(): DataT = storage.lastOrNull() ?: initialData

    /**
     * Removes [item] and returns [last] item along with the indication
     * if the removed element was the last one.
     */
    fun remove(item: DataT): RemovedDataInfo<DataT> {
        val isLast = item == storage.lastOrNull()
        storage.remove(item)
        return RemovedDataInfo(last(), isLast)
    }

    class RemovedDataInfo<T>(
        val newLast: T,
        val wasLast: Boolean,
    )
}
