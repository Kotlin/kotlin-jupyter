package org.jetbrains.kotlinx.jupyter.repl.embedded

import kotlin.reflect.KClass
import org.jetbrains.kotlinx.jupyter.api.DisplayResult

/**
 * Interface for classes that can store in-memory results from the REPL.
 *
 * This is only relevant for a kernel running in embedded mode, but here
 * it allows the frontend to directly access REPL results without having
 * to serialize them to JSON first.
 *
 * Each instance should be bound to a single jupyter session, and all ids
 * used should be unique within that session. Generally [DisplayResult.id]
 * should suffice.
 *
 * From the view of the [InMemoryReplResultsHolder], all values are opaque values,
 * the user of this interface should know what type it is.
 */
interface InMemoryReplResultsHolder {
    /**
     * Returns the REPL result for a given id or `null` if no result exists or `null` was the result.
     * @param id unique id for the given REPL result. Normally this is [DisplayResult.id].
     * @param type type of return value. `Any::class` can be used if it is uncertain what type it might be.
     */
    fun <T: Any> getReplResult(id: String, type: KClass<T>): T?

    /**
     * Sets the REPL result for a given id.
     * @param id unique id for the given REPL result. Normally this is [DisplayResult.id].
     * @param result the REPL result to store.
     */
    fun setReplResult(id: String, result: Any?)

    /**
     * Returns how many REPL results are currently being stored.
     */
    val size: Int
}

/**
 * Helper method to easily cast the return type using generics.
 */
inline fun <reified T: Any> InMemoryReplResultsHolder.getReplResult(id: String) = getReplResult(id, T::class)
