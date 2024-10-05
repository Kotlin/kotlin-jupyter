package org.jetbrains.kotlinx.jupyter.util

/**
 * An interface for strategies that delegate the class loading process.
 *
 * Classes implementing this interface can provide logic to determine how class loading
 * should be delegated to a parent class loader or another class loader.
 */
fun interface ClassLoadingDelegatingStrategy {
    fun delegateLoading(
        classFqn: String,
        parentLoader: ClassLoader,
    ): ClassLoader?
}
