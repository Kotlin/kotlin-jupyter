package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

interface VarState {
    val propertyKlass: KProperty<*>
    val scriptInstance: Any?
    val stringValue: String?
}

data class VariableState(
    override val propertyKlass: KProperty1<Any, *>,
    override val scriptInstance: Any,
) : VarState {
    private var _cachedValue: String? = null

    fun update() {
        _cachedValue = propertyKlass.get(scriptInstance).toString()
    }

    override val stringValue: String?
        get() = _cachedValue
}
