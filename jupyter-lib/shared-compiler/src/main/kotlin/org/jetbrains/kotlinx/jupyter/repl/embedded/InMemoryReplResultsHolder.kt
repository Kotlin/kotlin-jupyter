package org.jetbrains.kotlinx.jupyter.repl.embedded

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
     * @return the repl result associated with the given [id] or `null` if no result was found.
     */
    fun getReplResult(id: String): Any?

    /**
     * Add a REPL result without an ID. An ID will be auto-generated and returned.
     *
     * @param result the REPL result to store.
     * @return the id the [result] was stored under.
     */
    fun addReplResult(result: Any?): String

    /**
     * Sets the REPL result for a given id.
     * @param id unique id for the given REPL result. Normally this is [DisplayResult.id].
     * @param result the REPL result to store.
     */
    fun setReplResult(
        id: String,
        result: Any?,
    )

    /**
     * Removes the REPL result with the given [id] from the holder.
     * Returns `true` if an entry was removed, `false` if not.
     */
    fun removeReplResult(id: String): Boolean

    /**
     * Returns how many REPL results are currently being stored.
     */
    val size: Int
}
