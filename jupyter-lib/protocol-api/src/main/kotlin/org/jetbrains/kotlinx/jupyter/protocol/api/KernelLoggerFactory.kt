package org.jetbrains.kotlinx.jupyter.protocol.api

import org.slf4j.Logger
import kotlin.reflect.KClass

/**
 * Factory interface for creating slf4j-compatible loggers.
 * All components inside the kernel should only create loggers through this factory.
 */
interface KernelLoggerFactory {
    fun getLogger(category: String): Logger

    fun getLogger(clazz: Class<*>): Logger
}

fun KernelLoggerFactory.getLogger(kClass: KClass<*>): Logger = getLogger(kClass.java)

inline fun <reified T> KernelLoggerFactory.logger(): Logger = getLogger(T::class)
