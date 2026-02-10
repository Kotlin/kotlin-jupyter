package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo

class EmptyResolutionInfoProvider(
    private val libraryInfoCache: LibraryInfoCache,
) : ResolutionInfoProvider {
    override var fallback: LibraryResolutionInfo
        get() = ByNothingLibraryResolutionInfo
        set(_) {}

    override fun get(string: String): LibraryResolutionInfo {
        if (string.isEmpty()) return fallback
        return libraryInfoCache.getLibraryInfoByRef(string)
    }
}
