package org.jetbrains.kotlinx.jupyter.util

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.internal.KotlinReflectionInternalError

fun KClass<*>.isSubclassOfCatching(superType: KClass<*>): Boolean {
    return try {
        isSubclassOf(superType)
    } catch (e: UnsupportedOperationException) {
        false
    } catch (e: KotlinReflectionInternalError) {
        false
    } catch (e: InternalError) {
        // Workaround for #341
        false
    }
}
