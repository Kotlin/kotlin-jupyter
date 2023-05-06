package org.jetbrains.kotlinx.jupyter.api

interface ExtensionsProcessor<T : Any> {
    fun register(extension: T) = register(extension, ProcessingPriority.DEFAULT)
    fun register(extension: T, priority: Int)
    fun registerAll(extensions: Iterable<T>) {
        for (execution in extensions) {
            register(execution)
        }
    }
    fun unregister(extension: T)
    fun unregisterAll()
    fun registeredExtensions(): Collection<T>
    fun registeredExtensionsWithPriority(): List<Pair<T, Int>>
}
