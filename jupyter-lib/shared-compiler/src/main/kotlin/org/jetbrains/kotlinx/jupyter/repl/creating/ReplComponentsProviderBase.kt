package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.common.SimpleHttpClient
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryInfoCache
import org.jetbrains.kotlinx.jupyter.libraries.LibraryInfoCacheImpl
import org.jetbrains.kotlinx.jupyter.libraries.LibraryReferenceParser
import org.jetbrains.kotlinx.jupyter.libraries.LibraryReferenceParserImpl
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.messaging.CommunicationFacilityMock
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.DebugPortCommHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.ReplOptionsImpl
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.SessionOptionsImpl
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl
import org.jetbrains.kotlinx.jupyter.util.asCommonFactory
import java.io.File

abstract class ReplComponentsProviderBase : LazilyConstructibleReplComponentsProviderImpl() {
    override fun provideLoggerFactory(): KernelLoggerFactory = DefaultKernelLoggerFactory

    override fun provideResolutionInfoProvider(): ResolutionInfoProvider = EmptyResolutionInfoProvider(libraryInfoCache)

    override fun provideDisplayHandler(): DisplayHandler = NoOpDisplayHandler

    override fun provideNotebook(): MutableNotebook =
        NotebookImpl(
            loggerFactory,
            runtimeProperties,
            commManager,
            explicitClientType,
            librariesScanner,
            debugPort != null,
        )

    override fun provideScriptClasspath() = emptyList<File>()

    override fun provideHomeDir(): File? = null

    override fun provideMavenRepositories() = emptyList<MavenRepositoryCoordinates>()

    override fun provideLibraryResolver(): LibraryResolver? = null

    override fun provideRuntimeProperties(): ReplRuntimeProperties = defaultRuntimeProperties

    override fun provideScriptReceivers() = emptyList<Any>()

    override fun provideIsEmbedded() = false

    override fun provideLibrariesScanner(): LibrariesScanner = LibrariesScanner(loggerFactory)

    override fun provideCommManager(): CommManager = CommManagerImpl(communicationFacility)

    override fun provideCommHandlers(): List<CommHandler> =
        listOf(
            DebugPortCommHandler(),
        )

    override fun provideExplicitClientType(): JupyterClientType? = null

    override fun provideCommunicationFacility(): JupyterCommunicationFacility = CommunicationFacilityMock

    override fun provideDebugPort(): Int? = null

    override fun provideHttpClient(): HttpClient = SimpleHttpClient

    override fun provideLibraryDescriptorsManager(): LibraryDescriptorsManager =
        LibraryDescriptorsManager.getInstance(httpClient, loggerFactory.asCommonFactory())

    override fun provideLibraryInfoCache(): LibraryInfoCache = LibraryInfoCacheImpl(libraryDescriptorsManager)

    override fun provideLibraryInfoSwitcher(): ResolutionInfoSwitcher<DefaultInfoSwitch> =
        getDefaultResolutionInfoSwitcher(
            resolutionInfoProvider,
            libraryInfoCache,
            libraryDescriptorsManager.homeLibrariesDir(homeDir),
            runtimeProperties.currentBranch,
        )

    override fun provideLibrariesProcessor(): LibrariesProcessor {
        return LibrariesProcessorImpl(
            libraryResolver,
            libraryReferenceParser,
            runtimeProperties.version,
        )
    }

    override fun provideReplOptions(): ReplOptions {
        return ReplOptionsImpl()
    }

    override fun provideSessionOptions(): SessionOptions {
        return SessionOptionsImpl()
    }

    override fun provideMagicsHandler(): LibrariesAwareMagicsHandler? {
        return null
    }

    override fun provideLibraryReferenceParser(): LibraryReferenceParser = LibraryReferenceParserImpl(libraryInfoCache)

    override fun provideInMemoryReplResultsHolder(): InMemoryReplResultsHolder = NoOpInMemoryReplResultsHolder
}
