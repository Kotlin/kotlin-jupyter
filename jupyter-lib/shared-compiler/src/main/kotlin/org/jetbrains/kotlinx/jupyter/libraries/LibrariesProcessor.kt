package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer

interface LibrariesProcessor {
    fun processNewLibraries(arg: String): List<LibraryDefinitionProducer>
}
