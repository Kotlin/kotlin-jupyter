package org.jetbrains.kotlinx.jupyter.libraries

object EmptyResolutionInfoProvider : ResolutionInfoProvider {

    override var fallback: LibraryResolutionInfo
        get() = ByNothingLibraryResolutionInfo
        set(_) {}

    override fun get(string: String): LibraryResolutionInfo {
        if (string.isEmpty()) return fallback
        return AbstractLibraryResolutionInfo.getInfoByRef(string)
    }
}
