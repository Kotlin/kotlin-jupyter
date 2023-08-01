package org.jetbrains.kotlinx.jupyter.util

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

fun <A, V> createCachedFun(calculate: (A) -> V): (A) -> V {
    return createCachedFun({ it }, calculate)
}

fun <A, K, V> createCachedFun(calculateKey: (A) -> K, calculate: (A) -> V): (A) -> V {
    val cache = ConcurrentHashMap<Optional<K>, Optional<V>>()

    @Suppress("UNCHECKED_CAST")
    return { argument ->
        val key = Optional.ofNullable(calculateKey(argument)) as Optional<K>
        cache.getOrPut(key) {
            Optional.ofNullable(calculate(argument)) as Optional<V>
        }.orElse(null)
    }
}
