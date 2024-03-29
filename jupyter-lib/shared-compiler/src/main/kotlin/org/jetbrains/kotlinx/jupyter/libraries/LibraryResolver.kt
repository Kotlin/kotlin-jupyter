package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable

interface LibraryResolver {
    fun resolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition?
}
