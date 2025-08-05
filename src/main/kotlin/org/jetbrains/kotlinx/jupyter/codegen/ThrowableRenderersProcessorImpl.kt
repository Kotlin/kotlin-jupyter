package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderer
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryException
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException

class ThrowableRenderersProcessorImpl : ThrowableRenderersProcessor {
    private val renderers = mutableListOf<ThrowableRenderer>()

    override fun renderThrowable(throwable: Throwable): Any? =
        try {
            rethrowAsLibraryException(LibraryProblemPart.THROWABLE_RENDERERS) {
                renderers.firstOrNull { it.accepts(throwable) }?.render(throwable)
            }
        } catch (ex: ReplLibraryException) {
            """
            Exception thrown while rendering another exception. Original exception:
            ${throwable.stackTraceToString()}
            
            Rendering exception:
            ${ex.stackTraceToString()}
            """.trimIndent()
        }

    override fun register(renderer: ThrowableRenderer) {
        renderers.add(renderer)
    }
}
