package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.getHttp
import java.io.File
import java.net.URL

interface ResolutionInfoProvider {
    var fallback: LibraryResolutionInfo

    fun get(string: String): LibraryResolutionInfo

    companion object {
        fun withDefaultDirectoryResolution(dir: File) = StandardResolutionInfoProvider(
            LibraryResolutionInfo.ByDir(dir)
        )

        // Used in Kotlin Jupyter plugin for IDEA
        @Suppress("unused")
        fun withDefaultGitRefResolution(ref: String) = StandardResolutionInfoProvider(
            LibraryResolutionInfo.getInfoByRef(ref)
        )
    }
}

object EmptyResolutionInfoProvider : ResolutionInfoProvider {
    private val fallbackInfo = LibraryResolutionInfo.ByNothing()

    override var fallback: LibraryResolutionInfo
        get() = fallbackInfo
        set(_) {}

    override fun get(string: String): LibraryResolutionInfo {
        if (string.isEmpty()) return fallback
        return LibraryResolutionInfo.getInfoByRef(string)
    }
}

class StandardResolutionInfoProvider(override var fallback: LibraryResolutionInfo) : ResolutionInfoProvider {
    override fun get(string: String): LibraryResolutionInfo {
        if (string.isEmpty()) return fallback
        return tryGetAsRef(string) ?: tryGetAsDir(string) ?: tryGetAsFile(string) ?: tryGetAsURL(string) ?: fallback
    }

    private fun tryGetAsRef(ref: String): LibraryResolutionInfo? {
        val response = getHttp("$GitHubApiPrefix/contents/$RemoteLibrariesDir?ref=$ref")
        return if (response.status.successful) LibraryResolutionInfo.getInfoByRef(ref) else null
    }

    private fun tryGetAsDir(dirName: String): LibraryResolutionInfo? {
        val file = File(dirName)
        return if (file.isDirectory) LibraryResolutionInfo.ByDir(file) else null
    }

    private fun tryGetAsFile(fileName: String): LibraryResolutionInfo? {
        val file = File(fileName)
        return if (file.isFile) LibraryResolutionInfo.ByFile(file) else null
    }

    private fun tryGetAsURL(url: String): LibraryResolutionInfo? {
        val response = getHttp(url)
        return if (response.status.successful) LibraryResolutionInfo.ByURL(URL(url)) else null
    }
}
