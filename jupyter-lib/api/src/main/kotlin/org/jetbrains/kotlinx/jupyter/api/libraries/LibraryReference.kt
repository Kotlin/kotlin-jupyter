package org.jetbrains.kotlinx.jupyter.api.libraries

data class LibraryReference(
    val info: LibraryResolutionInfo,
    val name: String? = null,
) : LibraryCacheable by info {
    override fun toString(): String {
        val namePart = name ?: ""
        val infoPart = info.toString()
        return if (infoPart.isEmpty()) {
            namePart
        } else {
            "$namePart@$infoPart"
        }
    }
}
