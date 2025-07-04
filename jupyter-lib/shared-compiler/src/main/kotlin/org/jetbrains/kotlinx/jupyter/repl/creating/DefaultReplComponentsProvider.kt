package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.libraries.LibraryInfoCache
import org.jetbrains.kotlinx.jupyter.libraries.LibraryReferenceParser
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.SocketDisplayHandler
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.util.toUpperCaseAsciiOnly
import java.io.File

open class DefaultReplComponentsProvider(
    private val _settings: DefaultReplSettings,
    private val _communicationFacility: JupyterCommunicationFacility,
    private val _commManager: CommManager,
    private val _inMemoryResultsHolder: InMemoryReplResultsHolder,
) : ReplComponentsProviderBase() {
    private val logger by lazy {
        loggerFactory.getLogger(this::class)
    }

    override fun provideLoggerFactory(): KernelLoggerFactory = _settings.loggerFactory

    override fun provideResolutionInfoProvider(): ResolutionInfoProvider = _settings.replConfig.resolutionInfoProvider

    override fun provideDisplayHandler(): DisplayHandler = SocketDisplayHandler(_communicationFacility, notebook)

    override fun provideScriptClasspath(): List<File> = _settings.kernelConfig.ownParams.scriptClasspath

    override fun provideHomeDir(): File? = _settings.kernelConfig.ownParams.homeDir

    override fun provideMavenRepositories(): List<MavenRepositoryCoordinates> = _settings.replConfig.mavenRepositories

    override fun provideLibraryResolver(): LibraryResolver? = _settings.replConfig.libraryResolver

    override fun provideRuntimeProperties(): ReplRuntimeProperties = _settings.runtimeProperties

    override fun provideScriptReceivers(): List<Any> = _settings.replConfig.scriptReceivers

    override fun provideKernelRunMode(): KernelRunMode = _settings.replConfig.kernelRunMode

    override fun provideCommunicationFacility(): JupyterCommunicationFacility = _communicationFacility

    override fun provideCommManager(): CommManager = _commManager

    override fun provideDebugPort(): Int? = _settings.kernelConfig.ownParams.debugPort

    override fun provideExplicitClientType(): JupyterClientType? =
        _settings.kernelConfig.ownParams.clientType?.let { typeName ->
            try {
                JupyterClientType.valueOf(typeName.toUpperCaseAsciiOnly())
            } catch (_: IllegalArgumentException) {
                logger.warn("Unknown client type: $typeName")
                null
            }
        }

    private val httpUtil get() = _settings.replConfig.httpUtil

    override fun provideHttpClient(): HttpClient = httpUtil.httpClient

    override fun provideLibraryDescriptorsManager(): LibraryDescriptorsManager = httpUtil.libraryDescriptorsManager

    override fun provideLibraryInfoCache(): LibraryInfoCache = httpUtil.libraryInfoCache

    override fun provideLibraryReferenceParser(): LibraryReferenceParser = httpUtil.libraryReferenceParser

    override fun provideInMemoryReplResultsHolder(): InMemoryReplResultsHolder = _inMemoryResultsHolder

    override fun provideReplCompilerMode(): ReplCompilerMode = _settings.kernelConfig.ownParams.replCompilerMode

    override fun provideExtraCompilerArguments(): List<String> = _settings.kernelConfig.ownParams.extraCompilerArguments
}
