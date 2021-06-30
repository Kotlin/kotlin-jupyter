package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.typeOf

/**
 * Adds additional implicit receiver for subsequent cells
 */
@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> KotlinKernelHost.withReceiver(receiver: T) {
    withReceiver(receiver, typeOf<T>())
}

/**
 * Remove receiver with the given type from subsequent cells
 */
@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> KotlinKernelHost.removeReceiver() {
    removeReceiver(typeOf<T>())
}
