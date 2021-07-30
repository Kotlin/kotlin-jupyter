package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

interface LibraryResolver {
    fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition?
}
