package org.jetbrains.kotlinx.jupyter.util

/**
 * Delegates class loading to another class loader depending on the given [strategy]
 * [ClassLoadingDelegatingStrategy.delegateLoading] receives parent of this class loader as an argument
 * Usually, strategies should delegate to one of the parents or return null
 * meaning that the given class shouldn't be loaded
 */
class DelegatingClassLoader(
    parent: ClassLoader,
    private val strategy: ClassLoadingDelegatingStrategy,
) : ClassLoader(parent) {
    override fun loadClass(
        name: String?,
        resolve: Boolean,
    ): Class<*> {
        requireNotNull(name)
        val loader = strategy.delegateLoading(name, parent) ?: throw ClassNotFoundException(name)
        val c = loader.loadClass(name)
        if (resolve) {
            resolveClass(c)
        }
        return c
    }
}
