package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty1

data class VariableState(
    val propertyKlass: KProperty1<Any, *>,
    val scriptInstance: Any,
    var stringValue: String
)
