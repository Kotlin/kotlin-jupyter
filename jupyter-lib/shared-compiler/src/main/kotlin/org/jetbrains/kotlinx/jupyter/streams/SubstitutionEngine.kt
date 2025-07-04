package org.jetbrains.kotlinx.jupyter.streams

import org.jetbrains.kotlinx.jupyter.api.StreamSubstitutionType
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Creates new data using possibly existing initial data
 */
typealias DataFactory<DataT> = (initialData: DataT?) -> DataT

/**
 * Substitutes the shared data property with the `newData`. Returns previous value of the property
 */
typealias DataSubstitutor<DataT> = (newData: DataT) -> DataT

/**
 * Substitutes old data back to the property. Possibly destroys newData as long it's no longer necessary
 */
typealias DataFinalizer<DataT> = (newData: DataT, oldData: DataT) -> Unit

/**
 * Parameters of [SubstitutionEngine]
 */
class DataFlowComponents<DataT>(
    val initialData: DataT,
    val substitutor: DataSubstitutor<DataT>,
    val finalizer: DataFinalizer<DataT>,
)

/**
 * Handles the logic of how data is created, when it's substituted instead of the previous value
 * of a shared resource, and how it's substituted back
 */
abstract class SubstitutionEngine<DataT>(
    dataFlowComponents: DataFlowComponents<DataT>,
) {
    protected val initialData: DataT = dataFlowComponents.initialData
    protected val substitutor: DataSubstitutor<DataT> = dataFlowComponents.substitutor
    protected val finalizer: DataFinalizer<DataT> = dataFlowComponents.finalizer

    /**
     * Substitutes the data provided by [dataFactory] instead of a shared resource
     * (which is done by [substitutor]), executes [body] in this context and calls
     * [finalizer] afterwards. Note that [finalizer] is called even in the case of exceptional
     * completion of [body].
     */
    abstract fun <T> withDataSubstitution(
        dataFactory: DataFactory<DataT>,
        body: () -> T,
    ): T
}

/**
 * Implements the logic of substitution for [StreamSubstitutionType.BLOCKING]
 */
class BlockingSubstitutionEngine<DataT>(
    dataFlowComponents: DataFlowComponents<DataT>,
) : SubstitutionEngine<DataT>(dataFlowComponents) {
    private val lock = ReentrantLock()

    override fun <T> withDataSubstitution(
        dataFactory: DataFactory<DataT>,
        body: () -> T,
    ): T {
        val newData = dataFactory(initialData)
        return lock.withLock {
            val oldData = substitutor(newData)
            tryFinally(
                action = body,
                finally = { finalizer(newData, oldData) },
            )
        }
    }
}

/**
 * Implements the logic of substitution for [StreamSubstitutionType.NON_BLOCKING]
 */
class NonBlockingSubstitutionEngine<DataT : Any>(
    dataFlowComponents: DataFlowComponents<DataT>,
) : SubstitutionEngine<DataT>(dataFlowComponents) {
    private val lock = ReentrantLock()
    private val dataList = DataList(initialData)

    override fun <T> withDataSubstitution(
        dataFactory: DataFactory<DataT>,
        body: () -> T,
    ): T {
        val newData =
            lock.withLock {
                val myInitialData = dataList.last()
                val data = dataFactory(myInitialData)
                substitutor(data).also { _ ->
                    dataList.add(data)
                }
                data
            }

        return tryFinally(
            action = body,
            finally = {
                lock.withLock {
                    with(dataList.remove(newData)) {
                        if (wasLast) {
                            finalizer(newData, newLast)
                        }
                    }
                }
            },
        )
    }
}
