package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo

object ByNothingLibraryResolutionInfo : LibraryResolutionInfo {
    override val key: String
        get() = "nothing_"
}
