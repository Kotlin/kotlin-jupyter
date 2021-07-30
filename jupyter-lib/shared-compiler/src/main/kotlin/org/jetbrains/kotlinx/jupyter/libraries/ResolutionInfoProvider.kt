package org.jetbrains.kotlinx.jupyter.libraries

interface ResolutionInfoProvider {
    var fallback: LibraryResolutionInfo

    fun get(string: String): LibraryResolutionInfo
}
