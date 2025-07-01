package org.jetbrains.kotlinx.jupyter.util

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.internal.KotlinReflectionInternalError

fun KClass<*>.isSubclassOfCatching(superType: KClass<*>): Boolean =
    try {
        isSubclassOf(superType)
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: KotlinReflectionInternalError) {
        false
    } catch (_: InternalError) {
        // Workaround for #341
        false
    }
