package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.util.isSubclassOfCatching
import kotlin.reflect.KClass

interface ThrowableRenderer {
    fun accepts(throwable: Throwable): Boolean

    fun render(throwable: Throwable): Any
}

class SubtypeThrowableRenderer<E : Throwable>(
    private val superType: KClass<E>,
    private val renderer: (E) -> Any,
) : ThrowableRenderer {
    override fun accepts(throwable: Throwable): Boolean = throwable::class.isSubclassOfCatching(superType)

    override fun render(throwable: Throwable): Any {
        @Suppress("UNCHECKED_CAST")
        return renderer(throwable as E)
    }
}
