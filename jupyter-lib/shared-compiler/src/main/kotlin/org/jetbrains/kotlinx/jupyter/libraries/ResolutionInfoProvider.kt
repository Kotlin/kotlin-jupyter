package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo

interface ResolutionInfoProvider {
    var fallback: LibraryResolutionInfo

    fun get(string: String): LibraryResolutionInfo
}
