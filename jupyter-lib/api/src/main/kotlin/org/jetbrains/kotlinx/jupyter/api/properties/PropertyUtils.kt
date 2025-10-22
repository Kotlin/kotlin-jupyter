package org.jetbrains.kotlinx.jupyter.api.properties

import java.lang.reflect.Field
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

fun <T> KProperty1<T, *>.isLazy(receiver: T): Boolean {
    val delegate = getDelegate(receiver) ?: return false
    return delegate is Lazy<*>
}

fun <T : KProperty<*>, R> T.asAccessible(action: (T) -> R): R {
    val wasAccessible = isAccessible
    isAccessible = true
    return try {
        action(this)
    } finally {
        isAccessible = wasAccessible
    }
}

fun <T : KProperty<*>, R> T?.asAccessibleIfNotNull(action: (T?) -> R): R = this?.asAccessible(action) ?: action(null)

fun <R> Field.asAccessible(
    instance: Any,
    action: (Field) -> R,
): R {
    val isStatic = java.lang.reflect.Modifier.isStatic(modifiers)
    val wasAccessible = canAccess(if (isStatic) null else instance)
    if (!wasAccessible) {
        isAccessible = true
    }
    return try {
        action(this)
    } finally {
        if (!wasAccessible) {
            isAccessible = false
        }
    }
}
