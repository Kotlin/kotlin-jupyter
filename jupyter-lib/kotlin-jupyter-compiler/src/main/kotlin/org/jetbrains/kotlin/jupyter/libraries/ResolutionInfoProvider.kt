package org.jetbrains.kotlin.jupyter.libraries

import java.io.File
import java.net.URL

interface ResolutionInfoProvider {
    var fallback: LibraryResolutionInfo

    fun get(): LibraryResolutionInfo = fallback
    fun get(string: String): LibraryResolutionInfo
}

object EmptyResolutionInfoProvider : ResolutionInfoProvider {
    private val fallbackInfo = LibraryResolutionInfo.ByNothing()

    override var fallback: LibraryResolutionInfo
        get() = fallbackInfo
        set(_) {}

    override fun get(string: String) = LibraryResolutionInfo.getInfoByRef(string)
}

class StandardResolutionInfoProvider(override var fallback: LibraryResolutionInfo) : ResolutionInfoProvider {
    override fun get(string: String): LibraryResolutionInfo {
        return tryGetAsRef(string) ?: tryGetAsDir(string) ?: tryGetAsFile(string) ?: tryGetAsURL(string) ?: fallback
    }

    private fun tryGetAsRef(ref: String): LibraryResolutionInfo? {
        val response = khttp.get("$GitHubApiPrefix/contents/$LibrariesDir?ref=$ref")
        return if (response.statusCode == 200) LibraryResolutionInfo.getInfoByRef(ref) else null
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
        val response = khttp.get(url)
        return if (response.statusCode == 200) LibraryResolutionInfo.ByURL(URL(url)) else null
    }
}
