package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

data class LibraryReference(
    val info: LibraryResolutionInfo,
    val name: String? = null,
) : LibraryCacheable by info {

    val key: String

    init {
        val namePart = if (name.isNullOrEmpty()) "" else "${name}_"
        key = namePart + info.key
    }

    fun resolve(): LibraryDefinition? {
        val text = info.resolve(name) ?: return null
        return parseLibraryDescriptor(text)
    }

    override fun toString(): String {
        val namePart = name ?: ""
        val infoPart = info.toString()
        return if (infoPart.isEmpty()) namePart
        else "$namePart@$infoPart"
    }
}
