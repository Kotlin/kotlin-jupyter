package org.jetbrains.kotlinx.jupyter.testkit

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.api.libraries.libraryDefinition
import org.jetbrains.kotlinx.jupyter.libraries.ChainedLibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver

class ToEmptyLibraryResolver(
    parent: LibraryResolver?,
    private val resolveToEmpty: LibraryNameFilter,
) : ChainedLibraryResolver(parent) {
    override fun tryResolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? {
        return if (resolveToEmpty(reference.name)) return emptyLibraryDefinition else null
    }

    companion object {
        private val emptyLibraryDefinition = libraryDefinition {}
    }
}
