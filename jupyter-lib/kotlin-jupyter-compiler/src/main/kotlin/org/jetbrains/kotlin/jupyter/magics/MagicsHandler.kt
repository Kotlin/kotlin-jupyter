package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.api.LibraryDefinitionProducer
import org.jetbrains.kotlin.jupyter.common.ReplLineMagic

interface MagicsHandler {
    fun handle(magic: ReplLineMagic, arg: String?, tryIgnoreErrors: Boolean, parseOnly: Boolean)

    fun getLibraries(): List<LibraryDefinitionProducer>
}
