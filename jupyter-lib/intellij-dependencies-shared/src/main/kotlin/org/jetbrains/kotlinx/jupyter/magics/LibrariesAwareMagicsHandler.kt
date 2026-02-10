package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer

interface LibrariesAwareMagicsHandler : MagicsHandler {
    fun getLibraries(): List<LibraryDefinitionProducer>
}
