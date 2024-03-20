package org.jetbrains.kotlinx.jupyter.repl.embedded

import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Default implementation that just stores in-memory values in a [HashMap]
 * This also mean that they are live for the life-time of the jupyter session
 * or until a new REPL result is calculated for the same id, after which the
 * old result is GC'ed.
 */
class DefaultInMemoryReplResultsHolder : InMemoryReplResultsHolder {

    private val cache = mutableMapOf<String, Any?>()

    override fun <T : Any> getReplResult(id: String, type: KClass<T>): T? {
        return cache[id]?.let { result: Any ->
            type.cast(result)
        }
    }

    override fun setReplResult(id: String, result: Any?) {
        cache[id] = result
    }

    override val size: Int
        get() = cache.size
}
