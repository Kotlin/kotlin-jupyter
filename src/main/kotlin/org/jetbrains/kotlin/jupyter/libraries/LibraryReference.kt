package org.jetbrains.kotlin.jupyter.libraries

import org.jetbrains.kotlin.jupyter.LibraryDescriptor

data class LibraryReference(
    val info: LibraryResolutionInfo,
    val name: String? = null,
) : LibraryCacheable by info {

    val key: String

    init {
        val namePart = if (name.isNullOrEmpty()) "" else "${name}_"
        key = namePart + info.key
    }

    fun resolve(): LibraryDescriptor {
        val text = info.resolve(name)
        return parseLibraryDescriptor(text)
    }

    override fun toString(): String {
        val namePart = name ?: ""
        val infoPart = info.toString()
        return if (infoPart.isEmpty()) namePart
        else "$namePart@$infoPart"
    }
}
