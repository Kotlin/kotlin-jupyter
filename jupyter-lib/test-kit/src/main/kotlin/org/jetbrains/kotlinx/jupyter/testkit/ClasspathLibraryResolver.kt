package org.jetbrains.kotlinx.jupyter.testkit

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryReference
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.libraries.ChainedLibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor

class ClasspathLibraryResolver(parent: LibraryResolver? = null, val shouldResolve: (String?) -> Boolean = { true }) : ChainedLibraryResolver(parent) {
    override fun tryResolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
        if (!shouldResolve(reference.name)) return null
        val classloader = this::class.java.classLoader
        val resourceUrl = classloader.getResource("jupyterLibraries/${reference.name}.json") ?: return null
        val rawDescriptor = resourceUrl.readText()
        return parseLibraryDescriptor(rawDescriptor).convertToDefinition(arguments)
    }
}
