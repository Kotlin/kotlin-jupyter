package org.jetbrains.kotlinx.jupyter.libraries

import java.io.File

fun getStandardResolver(homeDir: String? = null, infoProvider: ResolutionInfoProvider): LibraryResolver {
    // Standard resolver doesn't cache results in memory
    var res: LibraryResolver = FallbackLibraryResolver
    val librariesDir: File? = homeDir?.let { KERNEL_LIBRARIES.homeLibrariesDir(File(it)) }
    res = LocalLibraryResolver(res, librariesDir)
    res = DefaultInfoLibraryResolver(res, infoProvider, listOf(KERNEL_LIBRARIES.userLibrariesDir))
    return res
}

fun getDefaultClasspathResolutionInfoProvider(): ResolutionInfoProvider {
    return StandardResolutionInfoProvider(
        AbstractLibraryResolutionInfo.ByClasspath,
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
        AbstractLibraryResolutionInfo.getInfoByRefWithFallback(defaultRef)
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
