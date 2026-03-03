package org.jetbrains.kotlinx.jupyter.libraries

interface LibraryInfoCache {
    fun getLibraryInfoByRef(reference: String): AbstractLibraryResolutionInfo.ByGitRef

    fun getLibraryInfoByRefWithFallback(reference: String): AbstractLibraryResolutionInfo.ByGitRefWithClasspathFallback
}
