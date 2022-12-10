package org.jetbrains.kotlinx.jupyter.testkit

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResourcesLibraryResolver

class ClasspathLibraryResolver(
    parent: LibraryResolver? = null,
    val shouldResolve: LibraryNameFilter = { true },
) : ResourcesLibraryResolver(parent, ClasspathLibraryResolver::class.java.classLoader) {
    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        if (!shouldResolve(reference.name)) return null
        return super.tryResolve(reference, arguments)
    }
}
