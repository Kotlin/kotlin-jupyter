package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import java.io.File

fun getStandardResolver(
    loggerFactory: KernelLoggerFactory,
    homeDir: String? = null,
    infoProvider: ResolutionInfoProvider,
    httpClient: HttpClient,
    libraryDescriptorsManager: LibraryDescriptorsManager,
): LibraryResolver {
    // Standard resolver doesn't cache results in memory
    var res: LibraryResolver = FallbackLibraryResolver(httpClient, libraryDescriptorsManager)
    val librariesDir: File? = homeDir?.let { libraryDescriptorsManager.homeLibrariesDir(File(it)) }
    res = LocalLibraryResolver(res, loggerFactory, libraryDescriptorsManager, librariesDir)
    res = DefaultInfoLibraryResolver(res, infoProvider, libraryDescriptorsManager, listOf(libraryDescriptorsManager.userLibrariesDir))
    return res
}

fun getDefaultClasspathResolutionInfoProvider(
    httpUtil: LibraryHttpUtil,
    loggerFactory: KernelLoggerFactory,
): ResolutionInfoProvider {
    return StandardResolutionInfoProvider(
        loggerFactory,
        AbstractLibraryResolutionInfo.ByClasspath,
        httpUtil,
    )
}

fun getDefaultResolutionInfoSwitcher(
    provider: ResolutionInfoProvider,
    libraryInfoCache: LibraryInfoCache,
    defaultDir: File,
    defaultRef: String,
): ResolutionInfoSwitcher<DefaultInfoSwitch> {
    val initialInfo = provider.fallback

    val dirInfo =
        if (initialInfo is AbstractLibraryResolutionInfo.ByDir) {
            initialInfo
        } else {
            AbstractLibraryResolutionInfo.ByDir(defaultDir)
        }

    val refInfo =
        if (initialInfo is AbstractLibraryResolutionInfo.ByGitRef) {
            initialInfo
        } else {
            libraryInfoCache.getLibraryInfoByRefWithFallback(defaultRef)
        }

    val classpathInfo = AbstractLibraryResolutionInfo.ByClasspath

    return ResolutionInfoSwitcher(provider, DefaultInfoSwitch.DIRECTORY) { switch ->
        when (switch) {
            DefaultInfoSwitch.DIRECTORY -> dirInfo
            DefaultInfoSwitch.GIT_REFERENCE -> refInfo
            DefaultInfoSwitch.CLASSPATH -> classpathInfo
        }
    }
}
