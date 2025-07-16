package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.ResolutionInfoProviderFactory
import java.io.File

fun getStandardResolver(
    infoProvider: ResolutionInfoProvider,
    httpClient: HttpClient,
    libraryDescriptorsManager: LibraryDescriptorsManager,
): LibraryResolver {
    // Standard resolver doesn't cache results in memory
    var res: LibraryResolver = FallbackLibraryResolver(httpClient, libraryDescriptorsManager)
    res = DefaultInfoLibraryResolver(res, infoProvider, libraryDescriptorsManager, listOf(libraryDescriptorsManager.userLibrariesDir))
    return res
}

object DefaultResolutionInfoProviderFactory : ResolutionInfoProviderFactory {
    override fun create(
        httpUtil: LibraryHttpUtil,
        loggerFactory: KernelLoggerFactory,
    ): ResolutionInfoProvider =
        StandardResolutionInfoProvider(
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
        initialInfo as? AbstractLibraryResolutionInfo.ByDir
            ?: AbstractLibraryResolutionInfo.ByDir(defaultDir)

    val refInfo =
        initialInfo as? AbstractLibraryResolutionInfo.ByGitRef
            ?: libraryInfoCache.getLibraryInfoByRefWithFallback(defaultRef)

    val classpathInfo = AbstractLibraryResolutionInfo.ByClasspath

    return ResolutionInfoSwitcher(provider, DefaultInfoSwitch.DIRECTORY) { switch ->
        when (switch) {
            DefaultInfoSwitch.DIRECTORY -> dirInfo
            DefaultInfoSwitch.GIT_REFERENCE -> refInfo
            DefaultInfoSwitch.CLASSPATH -> classpathInfo
        }
    }
}
