package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer

abstract class LibrariesAwareAbstractMagicsHandler :
    AbstractMagicsHandler(),
    LibrariesAwareMagicsHandler {
    protected val newLibraries: MutableList<LibraryDefinitionProducer> = mutableListOf()

    override fun getLibraries(): List<LibraryDefinitionProducer> {
        val librariesCopy = newLibraries.toList()
        newLibraries.clear()
        return librariesCopy
    }
}
