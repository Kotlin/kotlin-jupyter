package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer

object NoopMagicsHandler : LibrariesAwareMagicsHandler {
    override fun handle(
        magicText: String,
        tryIgnoreErrors: Boolean,
        parseOnly: Boolean,
    ) {
    }

    override fun getLibraries(): List<LibraryDefinitionProducer> = emptyList()
}
