package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.libraries.LibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import java.io.File

data class ReplConfig(
    val mavenRepositories: List<MavenRepositoryCoordinates> = listOf(),
    val libraryResolver: LibraryResolver? = null,
    val httpUtil: LibraryHttpUtil,
    val resolutionInfoProvider: ResolutionInfoProvider,
    val embedded: Boolean = false,
) {
    companion object {
        fun create(
            resolutionInfoProviderFactory: (LibraryHttpUtil, KernelLoggerFactory) -> ResolutionInfoProvider,
            loggerFactory: KernelLoggerFactory = DefaultKernelLoggerFactory,
            httpUtil: LibraryHttpUtil = createLibraryHttpUtil(loggerFactory),
            homeDir: File? = null,
            embedded: Boolean = false,
        ): ReplConfig {
            val resolutionInfoProvider = resolutionInfoProviderFactory(httpUtil, loggerFactory)

            return ReplConfig(
                mavenRepositories = defaultRepositoriesCoordinates,
                libraryResolver =
                    getStandardResolver(
                        loggerFactory,
                        homeDir?.toString(),
                        resolutionInfoProvider,
                        httpUtil.httpClient,
                        httpUtil.libraryDescriptorsManager,
                    ),
                httpUtil = httpUtil,
                resolutionInfoProvider = resolutionInfoProvider,
                embedded = embedded,
            )
        }
    }
}
