package org.jetbrains.kotlinx.jupyter.api

import org.slf4j.Logger
import kotlin.reflect.KClass

interface KernelLoggerFactory {
    fun getLogger(category: String): Logger

    fun getLogger(clazz: Class<*>): Logger
}

fun KernelLoggerFactory.getLogger(kClass: KClass<*>): Logger {
    return getLogger(kClass.java)
}

inline fun <reified T> KernelLoggerFactory.logger(): Logger {
    return getLogger(T::class)
}
