package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic

object NoopMagicsHandler : MagicsHandler {
    override fun handle(magic: ReplLineMagic, arg: String?, tryIgnoreErrors: Boolean, parseOnly: Boolean) {
    }

    override fun getLibraries(): List<LibraryDefinitionProducer> {
        return emptyList()
    }
}
