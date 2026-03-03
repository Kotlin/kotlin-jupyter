package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable

abstract class ChainedLibraryResolver(
    private val parent: LibraryResolver? = null,
) : LibraryResolver {
    protected abstract fun tryResolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition?

    protected open fun save(
        reference: LibraryReference,
        definition: LibraryDefinition,
    ) {}

    protected open fun shouldResolve(reference: LibraryReference): Boolean = true

    override fun resolve(
        reference: LibraryReference,
        arguments: List<Variable>,
    ): LibraryDefinition? {
        val shouldBeResolved = shouldResolve(reference)
        if (shouldBeResolved) {
            val result = tryResolve(reference, arguments)
            if (result != null) return result
        }

        val parentResult = parent?.resolve(reference, arguments) ?: return null
        if (shouldBeResolved) {
            save(reference, parentResult)
        }

        return parentResult
    }
}
