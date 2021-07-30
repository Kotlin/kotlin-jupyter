package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.getHttp
import java.io.File
import java.net.URL

class StandardResolutionInfoProvider(override var fallback: LibraryResolutionInfo) : ResolutionInfoProvider {
    override fun get(string: String): LibraryResolutionInfo {
        if (string.isEmpty()) return fallback
        return tryGetAsRef(string) ?: tryGetAsDir(string) ?: tryGetAsFile(string) ?: tryGetAsURL(string) ?: fallback
    }

    private fun tryGetAsRef(ref: String): LibraryResolutionInfo? {
        return if (KERNEL_LIBRARIES.checkRefExistence(ref)) AbstractLibraryResolutionInfo.getInfoByRef(ref) else null
    }

    private fun tryGetAsDir(dirName: String): LibraryResolutionInfo? {
        val file = File(dirName)
        return if (file.isDirectory) AbstractLibraryResolutionInfo.ByDir(file) else null
    }

    private fun tryGetAsFile(fileName: String): LibraryResolutionInfo? {
        val file = File(fileName)
        return if (file.isFile) AbstractLibraryResolutionInfo.ByFile(file) else null
    }

    private fun tryGetAsURL(url: String): LibraryResolutionInfo? {
        val response = getHttp(url)
        return if (response.status.successful) AbstractLibraryResolutionInfo.ByURL(URL(url)) else null
    }
}
