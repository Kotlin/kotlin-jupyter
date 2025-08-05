package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.InternalVariablesMarker
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor
import kotlin.reflect.KProperty

class InternalVariablesMarkersProcessorImpl : InternalVariablesMarkersProcessor {
    private val markers = mutableListOf<InternalVariablesMarker>()

    override fun registerAll(markers: Iterable<InternalVariablesMarker>) {
        this.markers.addAll(markers)
    }

    override fun isInternal(property: KProperty<*>): Boolean =
        rethrowAsLibraryException(LibraryProblemPart.INTERNAL_VARIABLES_MARKERS) {
            markers.any { it.isInternal(property) }
        }
}
