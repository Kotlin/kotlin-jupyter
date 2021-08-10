package org.jetbrains.kotlinx.jupyter.api

import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

interface VariableState {
    val property: KProperty<*>
    val scriptInstance: Any?
    val stringValue: String?
    val value: Any?
}

data class VariableStateImpl(
    override val property: KProperty1<Any, *>,
    override val scriptInstance: Any,
) : VariableState {
    private var cachedValue: Any? = null
    private var isRecursive: Boolean = false

    fun update() {
        val wasAccessible = property.isAccessible
        property.isAccessible = true
        val fieldValue = property.get(scriptInstance)
        property.isAccessible = wasAccessible
        cachedValue = fieldValue
        handleRecursiveCase()
    }

    override val stringValue: String?
        get() = getProperString()

    override val value: Any?
        get() = cachedValue

    private fun handleRecursiveCase() {
        if (cachedValue == null) return
        val seenNodes = mutableSetOf<Any>()
        goThrough(cachedValue, seenNodes)
    }

    private fun getProperString(): String? {
        fun getRecursiveObjectCode(): String {
            val kClassName = cachedValue!!::class.simpleName
            return buildString {
                append("$kClassName: ")
                append("recursive structure")
            }
        }
        if (cachedValue == null) return null
        return if (!isRecursive) {
            cachedValue.toString()
        } else {
            getRecursiveObjectCode()
        }
    }

    private fun goThrough(value: Any?, seenNodes: MutableSet<Any>) {
        if (value == null) return
        val membersProperties = try {
            value::class.declaredMemberProperties
        } catch (e: Error) {
            emptyList<Collection<KProperty1<Any, *>>>()
        } catch (e: Exception) {
            emptyList<Collection<KProperty1<Any, *>>>()
        }

        val receivedInstances: MutableList<Any?> = mutableListOf()
        for (property in membersProperties) {
            property as KProperty1<Any, *>
            try {
                val wasAccessible = property.isAccessible
                property.isAccessible = true
                val callInstance = property.get(value)
                property.isAccessible = wasAccessible
                val wasSeen = if (callInstance != null) !seenNodes.add(callInstance) else false

                if (wasSeen) {
                    isRecursive = true
                    return
                }
                receivedInstances.add(callInstance)
            } catch (ex: Error) {
                if (ex is StackOverflowError) {
                    isRecursive = true
                }
                return
            } catch (ex: InvocationTargetException) {
                return
            }
        }
        receivedInstances.forEach {
            goThrough(it, seenNodes)
        }
    }
}
