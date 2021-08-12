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
    private var cachedValue: Result<Any?> = Result.success(null)
    private var isRecursive: Boolean = false

    fun update(): Boolean {
        val wasAccessible = property.isAccessible
        property.isAccessible = true
        val fieldValue = try {
            Result.success(property.get(scriptInstance))
        } catch (ex: Throwable) {
            Result.failure(ex)
        }
        property.isAccessible = wasAccessible

        val isChanged = cachedValue.getOrNull() !== fieldValue.getOrNull()
        cachedValue = fieldValue
        return isChanged
    }

    override val stringValue: String? by lazy {
        fun getRecursiveObjectName(): String {
            val kClassName = cachedValue.getOrNull()!!::class.simpleName
            return "$kClassName: recursive structure"
        }
        if (cachedValue.getOrNull() == null) {
            return@lazy null
        }
        handleIfRecursiveStructure()

        if (!isRecursive) {
            cachedValue.getOrNull().toString()
        } else {
            getRecursiveObjectName()
        }
    }

    override val value: Result<Any?>
        get() = cachedValue

    private fun handleIfRecursiveStructure() {
        if (cachedValue.getOrNull() == null) return
        traverseObject(cachedValue.getOrNull(), mutableSetOf())
    }

    private fun traverseObject(value: Any?, seenNodes: MutableSet<Any>) {
        if (value == null) return
        val membersProperties = try {
            value::class.declaredMemberProperties
        } catch (ex: Throwable) {
            emptyList<Collection<KProperty1<Any, *>>>()
        }

        val receivedInstances: MutableList<Any?> = mutableListOf()
        for (property in membersProperties) {
            @Suppress("UNCHECKED_CAST")
            property as KProperty1<Any, *>
            try {
                val wasAccessible = property.isAccessible
                property.isAccessible = true
                val callInstance = property.get(value)
                property.isAccessible = wasAccessible
                val wasSeen = callInstance != null && !seenNodes.add(callInstance)

                if (wasSeen) {
                    isRecursive = true
                    return
                }
                receivedInstances.add(callInstance)
            } catch (ex: Throwable) {
                // there might be recursive elements inside the container
                if (ex is StackOverflowError) {
                    isRecursive = true
                }
                return
            }
        }
        receivedInstances.forEach {
            traverseObject(it, seenNodes)
        }
    }
}
