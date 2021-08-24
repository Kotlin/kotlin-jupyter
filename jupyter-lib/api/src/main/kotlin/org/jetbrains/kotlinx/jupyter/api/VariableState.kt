package org.jetbrains.kotlinx.jupyter.api

import java.lang.reflect.Field
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
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
                "${value::class.simpleName}: [exception thrown: $e]"
            }
        }
    }
    override var isRecursive: Boolean = false
    private var isLargeForString: Boolean = false

    private val valCache = VariableStateCache<Result<Any?>> (
        {
            oldValue, newValue ->
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
    }
            private val customDelegate = DependentLazyDelegate {
                fun getRecursiveObjectName(): String {
                    val kClassName = cachedValue.getOrNull()!!::class.simpleName
                    return "$kClassName: recursive structure"
                }
                if (cachedValue.getOrNull() == null) {
                    return@DependentLazyDelegate null
                }
                handleIfRecursiveStructure()
                try {
                    cachedValue.getOrNull().toString()
                    isRecursive = false
                    isLargeForString = false
                } catch (e: VirtualMachineError) {
                    when (e) {
                        is StackOverflowError -> {
                            isRecursive = true
                        }
                        is OutOfMemoryError -> {
                            isLargeForString = true
                        }
                        else -> {
                            return@DependentLazyDelegate null
                        }
                    }
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
