package org.jetbrains.kotlinx.jupyter.magics.contexts

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher

/**
 * Context interface for handlers that need to work with libraries.
 * Provides access to the library processor and resolution info switcher.
 */
class LibrariesMagicHandlerContext(
    /**
     * Processor for handling libraries.
     */
    val librariesProcessor: LibrariesProcessor,
    /**
     * Switcher for controlling library resolution strategy.
     */
    val libraryResolutionInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
) : MagicHandlerContext {
    /**
     * List of new libraries added by magic commands.
     */
    private val newLibraries: MutableList<LibraryDefinitionProducer> = mutableListOf()

    /**
     * Adds libraries to the context
     */
    fun addLibraries(libraries: Iterable<LibraryDefinitionProducer>) {
        newLibraries.addAll(libraries)
    }

    /**
     * Gets the list of libraries and clears the internal list.
     */
    fun getLibraries(): List<LibraryDefinitionProducer> {
        val librariesCopy = newLibraries.toList()
        newLibraries.clear()
        return librariesCopy
    }
}
