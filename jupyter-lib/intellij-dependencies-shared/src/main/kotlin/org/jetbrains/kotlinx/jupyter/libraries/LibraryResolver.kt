package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable

/**
 * Interface for resolving libraries based on a given reference and arguments.
 *
 * Example:
 * %use dataframe(0.10.0-dev-1373)
 */
interface LibraryResolver {
    fun resolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition?
}
