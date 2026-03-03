package org.jetbrains.kotlinx.jupyter.magics.contexts

import kotlin.reflect.KClass
import kotlin.reflect.safeCast

inline fun <reified T : MagicHandlerContext> MagicHandlerContext.getContext(): T? = getContext(T::class)

inline fun <reified T : MagicHandlerContext> MagicHandlerContext.requireContext(): T =
    requireNotNull(getContext<T>()) {
        "Context ${T::class.simpleName} is required for $this"
    }

fun <T : MagicHandlerContext> MagicHandlerContext.getContext(kClass: KClass<T>): T? {
    val asTSafe = kClass.safeCast(this)
    return when {
        asTSafe != null -> asTSafe
        this is CompositeMagicHandlerContext -> contexts.asSequence().mapNotNull { it.getContext(kClass) }.firstOrNull()
        else -> null
    }
}
