package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest

interface LibrariesProcessor {
    val requests: Collection<LibraryResolutionRequest>

    fun processNewLibraries(arg: String): List<LibraryDefinitionProducer>
}
