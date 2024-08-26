package org.jetbrains.kotlinx.jupyter.util

/**
 * Represents a sequence of closable data items.
 * This structure is NOT thread-safe.
 * Synchronization should be performed externally.
 *
 * @param DataT The type of data in the sequence.
 * @property initialData The initial data item in the sequence.
 * @constructor Creates a [ClosableDataSequence] with the initial data item.
 */
class ClosableDataSequence<DataT>(
    private val initialData: DataT,
) {
    class DataInfo<T>(
        val data: T,
        val isLast: Boolean,
    )

    private class InternalDataInfo<T>(
        val data: T,
        val dataNumber: Int,
        @Volatile
        var closed: Boolean = false,
    )

    private val storage = mutableMapOf<DataT, InternalDataInfo<DataT>>()
    private var storageSize = 0
    private var storageCounter = 0

    /**
     * Adds [newData] with its parent [oldData] in not-closed state
     */
    fun add(
        newData: DataT,
        oldData: DataT,
    ) {
        ++storageSize
        val dataNumber = ++storageCounter
        storage[newData] = InternalDataInfo(oldData, dataNumber)
    }

    fun last(): DataT {
        return storage.entries
            .firstOrNull { it.value.dataNumber == storageCounter }
            ?.value?.data ?: initialData
    }

    /**
     * Closes [newData].
     */
    fun close(newData: DataT): DataInfo<DataT> {
        val dataInfo = storage[newData] ?: throw IllegalStateException("$newData was already closed or was never added")
        dataInfo.closed = true
        val oldData = dataInfo.data
        val isLast = dataInfo.dataNumber == storageCounter
        var currentData = oldData
        while (true) {
            val parentInfo = storage[currentData] ?: break
            if (parentInfo.closed) {
                currentData = parentInfo.data
            } else {
                break
            }
        }
        --storageSize
        if (storageSize == 0) {
            storage.clear()
        }
        return DataInfo(currentData, isLast)
    }
}
