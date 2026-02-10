package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.util.createCachedFun

class LibraryInfoCacheImpl(
    private val libraryDescriptorsManager: LibraryDescriptorsManager,
) : LibraryInfoCache {
    override fun getLibraryInfoByRef(reference: String): AbstractLibraryResolutionInfo.ByGitRef = infoByRef(reference)

    override fun getLibraryInfoByRefWithFallback(reference: String): AbstractLibraryResolutionInfo.ByGitRefWithClasspathFallback =
        infoByRefWithFallback(reference)

    private val infoByRef =
        createCachedFun { ref: String ->
            AbstractLibraryResolutionInfo.ByGitRef(ref, libraryDescriptorsManager)
        }

    private val infoByRefWithFallback =
        createCachedFun { ref: String ->
            AbstractLibraryResolutionInfo.ByGitRefWithClasspathFallback(ref, libraryDescriptorsManager)
        }
}
