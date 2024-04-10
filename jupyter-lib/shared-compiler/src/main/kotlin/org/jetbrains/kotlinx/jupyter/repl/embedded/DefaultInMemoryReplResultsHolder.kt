package org.jetbrains.kotlinx.jupyter.repl.embedded

import kotlin.random.Random

/**
 * Default implementation that just stores in-memory values in a [HashMap]
 * This also mean that they are live for the life-time of the jupyter session
 * or until a new REPL result is calculated for the same id, after which the
 * old result is GC'ed.
 */
class DefaultInMemoryReplResultsHolder : InMemoryReplResultsHolder {
    private val cache = mutableMapOf<String, Any?>()

    override fun getReplResult(id: String): Any? {
        return cache[id]
    }

    private fun nextRandomId(): String {
        // Make a reasonable attempt at avoiding conflicts with
        // user-provided ids.
        var newId = "generated-${Random.nextInt()}"
        while (cache[newId] != null) {
            newId = "generated-${Random.nextInt()}"
        }
        return newId
    }

    override fun addReplResult(result: Any?): String {
        return nextRandomId().also { id ->
            cache[id] = result
        }
    }

    override fun setReplResult(
        id: String,
        result: Any?,
    ) {
        cache[id] = result
    }

    override fun removeReplResult(id: String): Boolean {
        return cache.remove(id) != null
    }

    override val size: Int
        get() = cache.size
}
