package org.jetbrains.kotlinx.jupyter.api.libraries

data class LibraryReference(
    val info: LibraryResolutionInfo,
    val name: String? = null,
) : LibraryCacheable by info {
    val key by lazy {
        val namePart = if (name.isNullOrEmpty()) "" else "${name}_"
        namePart + info.key
    }

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
