package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.config.errorForUser
import org.jetbrains.kotlinx.jupyter.config.getLogger
import java.io.File

val KERNEL_LIBRARIES = LibraryDescriptorsManager.getInstance(
    getLogger()
) { logger, message, exception ->
    logger.errorForUser(message = message, throwable = exception)
}

fun getStandardResolver(homeDir: String? = null, infoProvider: ResolutionInfoProvider): LibraryResolver {
    // Standard resolver doesn't cache results in memory
    var res: LibraryResolver = FallbackLibraryResolver
    val librariesDir: File? = homeDir?.let { KERNEL_LIBRARIES.homeLibrariesDir(File(it)) }
    res = ResourcesLibraryResolver(res, Thread.currentThread().contextClassLoader)
    res = LocalLibraryResolver(res, librariesDir)
    res = DefaultInfoLibraryResolver(res, infoProvider, listOf(KERNEL_LIBRARIES.userLibrariesDir))
    return res
}

fun getDefaultDirectoryResolutionInfoProvider(dir: File): ResolutionInfoProvider {
    return StandardResolutionInfoProvider(
        AbstractLibraryResolutionInfo.ByDir(dir)
    )
}

fun getDefaultResolutionInfoSwitcher(provider: ResolutionInfoProvider, defaultDir: File, defaultRef: String): ResolutionInfoSwitcher<DefaultInfoSwitch> {
    val initialInfo = provider.fallback

    val dirInfo = if (initialInfo is AbstractLibraryResolutionInfo.ByDir) {
        initialInfo
    } else {
        AbstractLibraryResolutionInfo.ByDir(defaultDir)
    }

    val refInfo = if (initialInfo is AbstractLibraryResolutionInfo.ByGitRef) {
        initialInfo
    } else {
        AbstractLibraryResolutionInfo.getInfoByRef(defaultRef)
    }

    return ResolutionInfoSwitcher(provider, DefaultInfoSwitch.DIRECTORY) { switch ->
        when (switch) {
            DefaultInfoSwitch.DIRECTORY -> dirInfo
            DefaultInfoSwitch.GIT_REFERENCE -> refInfo
        }
    }
}
