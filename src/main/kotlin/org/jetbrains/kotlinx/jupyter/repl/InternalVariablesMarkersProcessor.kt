package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.InternalVariablesMarker
import kotlin.reflect.KProperty

interface InternalVariablesMarkersProcessor {
    fun registerAll(markers: Iterable<InternalVariablesMarker>)
    fun isInternal(property: KProperty<*>): Boolean
}
