package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KProperty

fun interface InternalVariablesMarker {
    fun isInternal(variable: KProperty<*>): Boolean
}
