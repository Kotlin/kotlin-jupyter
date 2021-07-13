package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
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

    fun update() {
        val wasAccessible = property.isAccessible
        property.isAccessible = true
        val fieldValue = property.get(scriptInstance)
        property.isAccessible = wasAccessible
        cachedValue = fieldValue
    }

    override val stringValue: String?
        get() = cachedValue?.toString()

    override val value: Any?
        get() = cachedValue
}
