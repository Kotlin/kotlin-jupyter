package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

interface VariableState {
    val property: KProperty<*>
    val scriptInstance: Any?
    val stringValue: String?
    val value: Result<Any?>
}

data class VariableStateImpl(
    override val property: KProperty1<Any, *>,
    override val scriptInstance: Any,
) : VariableState {
    private val stringCache = VariableStateCache<String?> {
        value.getOrNull()?.let { value ->
            if (value.isRecursiveStructure()) {
                "${value::class.simpleName}: recursive structure"
            } else {
                value.toString()
            }
        }
    }

    private val valCache = VariableStateCache<Result<Any?>> {
        property.asAccessible { prop ->
            try {
                Result.success(prop.get(scriptInstance))
            } catch (ex: Throwable) {
                Result.failure(ex)
            }
        }
    }

    fun update(): Boolean {
        val oldValue = value
        valCache.update()
        val newValue = value

        return (oldValue.getOrNull() !== newValue.getOrNull()).also { isChanged ->
            if (isChanged) stringCache.update()
        }
    }

    override val stringValue: String? get() = stringCache.get()

    override val value: Result<Any?> get() = valCache.get()

    companion object {
        private fun <T : KProperty<*>, R> T.asAccessible(action: (T) -> R): R {
            val wasAccessible = isAccessible
            isAccessible = true
            val res = action(this)
            isAccessible = wasAccessible
            return res
        }

        private fun Any?.isRecursiveStructure(): Boolean {
            return traverseObject(this, mutableSetOf())
        }

        private fun traverseObject(value: Any?, seenNodes: MutableSet<Any>): Boolean {
            if (value == null) return false
            return try {
                value::class.declaredMemberProperties
            } catch (ex: Throwable) {
                return false
            }.mapNotNull { property ->
                @Suppress("UNCHECKED_CAST")
                property as KProperty1<Any, *>

                try {
                    val callInstance = property.asAccessible {
                        it.get(value)
                    }

                    if (callInstance != null && !seenNodes.add(callInstance)) {
                        return true
                    }
                    callInstance
                } catch (ex: Throwable) {
                    // there might be recursive elements inside the container
                    if (ex is StackOverflowError) {
                        return true
                    } else {
                        null
                    }
                }
            }.any {
                traverseObject(it, seenNodes)
            }
        }
    }
}

private class VariableStateCache<T>(val calculate: (T?) -> T) {
    private var cachedVal: T? = null
    private var shouldRenew: Boolean = true

    fun get(): T {
        return if (shouldRenew) {
            calculate(cachedVal).also {
                cachedVal = it
                shouldRenew = false
            }
        } else {
            cachedVal!!
        }
    }

    fun update() {
        shouldRenew = true
    }
}
