package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.StandaloneKernelRunMode
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.libraries.LibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

data class ReplConfig(
    val mavenRepositories: List<MavenRepositoryCoordinates> = listOf(),
    val libraryResolver: LibraryResolver? = null,
    val httpUtil: LibraryHttpUtil,
    val resolutionInfoProvider: ResolutionInfoProvider,
    val kernelRunMode: KernelRunMode,
    val scriptReceivers: List<Any> = emptyList(),
) {
    companion object {
        fun create(
            resolutionInfoProviderFactory: ResolutionInfoProviderFactory,
            loggerFactory: KernelLoggerFactory = DefaultKernelLoggerFactory,
            httpUtil: LibraryHttpUtil = createLibraryHttpUtil(loggerFactory),
            kernelRunMode: KernelRunMode = StandaloneKernelRunMode,
            scriptReceivers: List<Any>? = emptyList(),
        ): ReplConfig {
            val resolutionInfoProvider = resolutionInfoProviderFactory.create(httpUtil, loggerFactory)

            return ReplConfig(
                mavenRepositories = defaultRepositoriesCoordinates,
                libraryResolver =
                    getStandardResolver(
                        resolutionInfoProvider,
                        httpUtil.httpClient,
                        httpUtil.libraryDescriptorsManager,
                    ),
                httpUtil = httpUtil,
                resolutionInfoProvider = resolutionInfoProvider,
                kernelRunMode = kernelRunMode,
                scriptReceivers = scriptReceivers.orEmpty(),
            )
        }
    }
}
