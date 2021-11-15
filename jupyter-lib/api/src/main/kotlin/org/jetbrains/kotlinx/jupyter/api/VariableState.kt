package org.jetbrains.kotlinx.jupyter.api

import java.lang.reflect.Field
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.isAccessible

interface VariableState {
    val property: Field
    val scriptInstance: Any?
    val stringValue: String?
    val value: Result<Any?>
    val isRecursive: Boolean
}

class DependentLazyDelegate<T>(val initializer: () -> T?) {
    private var cachedPropertyValue: T? = null
    var isChanged: Boolean = true

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        if (isChanged) {
            cachedPropertyValue = initializer()
        }
        return cachedPropertyValue
    }
}

data class VariableStateImpl(
    override val property: Field,
    override val scriptInstance: Any,
) : VariableState {
    private val stringCache = VariableStateCache<String?> {
        value.getOrNull()?.let { value ->
            try {
                value.toString()
            } catch (e: Throwable) {
                if (e is StackOverflowError) {
                    isRecursive = true
                }
                "${value::class.simpleName}: [exception thrown: $e]"
            }
        }
    }
    override var isRecursive: Boolean = false

    private val valCache = VariableStateCache<Result<Any?>>(
        { oldValue, newValue ->
            oldValue.getOrNull() !== newValue.getOrNull()
        },
        {
            property.asAccessible { prop ->
                try {
                    Result.success(prop.get(scriptInstance))
                } catch (ex: Throwable) {
                    Result.failure(ex)
                }
            }
        }
    )

    fun update(): Boolean {
        return (valCache.forceUpdate()).also { isChanged ->
            if (isChanged) stringCache.update()
        }
    }

    override val stringValue: String? get() = stringCache.getOrNull()

    override val value: Result<Any?> get() = valCache.get()

    companion object {
        @SuppressWarnings("DEPRECATED")
        private fun <R> Field.asAccessible(action: (Field) -> R): R {
            val wasAccessible = isAccessible
            isAccessible = true
            val res = action(this)
            isAccessible = wasAccessible
            return res
        }
    }

    private class VariableStateCache<T>(
        val equalityChecker: (T, T) -> Boolean = { x, y -> x == y },
        val calculate: (T?) -> T
    ) {
        private var cachedVal: T? = null
        private var shouldRenew: Boolean = true

        fun getOrNull(): T? {
            return if (shouldRenew) {
                calculate(cachedVal).also {
                    cachedVal = it
                    shouldRenew = false
                }
            } else {
                cachedVal
            }
        }

        fun get(): T = getOrNull()!!

        fun update() {
            shouldRenew = true
        }

        fun forceUpdate(): Boolean {
            val oldVal = getOrNull()
            update()
            val newVal = get()
            return oldVal != null && equalityChecker(oldVal, newVal)
        }
    }
}
