package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

interface VariableState {
    val propertyKlass: KProperty<*>
    val scriptInstance: Any?
    val stringValue: String?
}

data class VariableStateImpl(
    override val propertyKlass: KProperty1<Any, *>,
    override val scriptInstance: Any,
) : VariableState {
    private var cachedValue: String? = null

    fun update() {
        val wasAccessible = propertyKlass.isAccessible
        propertyKlass.isAccessible = true
        val fieldValue = propertyKlass.get(scriptInstance)
        propertyKlass.isAccessible = wasAccessible
        cachedValue = fieldValue.toString()
    }

    override val stringValue: String?
        get() = cachedValue
}
