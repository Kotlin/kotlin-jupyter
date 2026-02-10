package org.jetbrains.kotlinx.jupyter.util

interface Provider<T : Any> {
    fun provide(): T?
}

interface UpdatableProvider<T : Any> : Provider<T> {
    fun update(value: T)
}

open class UpdatableProviderImpl<T : Any> : UpdatableProvider<T> {
    private var value: T? = null

    override fun provide(): T? = value

    override fun update(value: T) {
        this.value = value
    }
}
