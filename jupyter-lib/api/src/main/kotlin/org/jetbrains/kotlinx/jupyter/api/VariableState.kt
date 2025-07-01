package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
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
    // Just a Boolean rather than ReplCompilerMode as we cannot access the ReplCompilerMode
    // enum from here, due to the way modules depend on each other.
    val compilerMode: ReplCompilerMode,
) : VariableState {
    private val stringCache =
        VariableStateCache<String?> {
            value.getOrNull()?.let { value ->
                try {
                    value.toString()
                } catch (e: Throwable) {
                    "${value::class.simpleName}: [exception thrown: $e]"
                }
            }
        }

    private val valCache =
        VariableStateCache<Result<Any?>>(
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
            },
        )

    fun update(): Boolean =
        (valCache.forceUpdate()).also { isChanged ->
            if (isChanged) stringCache.update()
        }

    override val stringValue: String? get() = stringCache.get()

    override val value: Result<Any?> get() = valCache.get()

    companion object {
        // We can currently not modify the accessible state in K2.
        // See https://youtrack.jetbrains.com/issue/KT-75580/K2-Repl-Cannot-access-snippet-properties-using-Kotlin-reflection
        private fun <T : KProperty<*>, R> T.asAccessible(action: (T) -> R): R {
            val wasAccessible = isAccessible
            isAccessible = true
            val res = action(this)
            isAccessible = wasAccessible
            return res
        }
    }
}

private class VariableStateCache<T>(
    val equalityChecker: (T, T) -> Boolean = { x, y -> x == y },
    val calculate: (T?) -> T,
) {
    private var cachedVal: T? = null
    private var shouldRenew: Boolean = true

    fun getOrNull(): T? =
        if (shouldRenew) {
            calculate(cachedVal).also {
                cachedVal = it
                shouldRenew = false
            }
        } else {
            cachedVal
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
