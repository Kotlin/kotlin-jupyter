package org.jetbrains.kotlinx.jupyter.repl.embedded

import kotlin.reflect.KClass

/**
 * Interface for classes that can store in-memory results from the REPL.
 *
 * This is only relevant for a kernel running in embedded mode, but then
 * it allows the frontend to directly access REPL results without having
 * to serialize them to JSON first.
 *
 * Each instance should be bound to a single jupyter session, and all ids
 * used should be unique within that session. Generally
 * [org.jetbrains.kotlinx.jupyter.api.DisplayResult.id] should suffice.
 *
 * From the view of the [InMemoryReplResultsHolder], all values are opaque values,
 * the user of this interface should know what type it is.
 */
interface InMemoryReplResultsHolder {
    /**
     * TODO
     */
    fun <T: Any> getReplResult(id: String, type: KClass<T>): T?

    /**
     * TODO
     */
    fun setReplResult(id: String, result: Any?)

    /**
     * TODO
     */
    val size: Int
}

inline fun <reified T: Any> InMemoryReplResultsHolder.getReplResult(id: String) = getReplResult(id, T::class)
