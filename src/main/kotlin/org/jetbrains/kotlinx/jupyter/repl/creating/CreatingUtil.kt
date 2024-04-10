package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.CommunicationFacilityMock
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import java.io.File

fun createRepl(
    httpUtil: LibraryHttpUtil,
    resolutionInfoProvider: ResolutionInfoProvider = EmptyResolutionInfoProvider(httpUtil.libraryInfoCache),
    scriptClasspath: List<File> = emptyList(),
    homeDir: File? = null,
    mavenRepositories: List<MavenRepositoryCoordinates> = listOf(),
    libraryResolver: LibraryResolver? = null,
    runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    scriptReceivers: List<Any> = emptyList(),
    isEmbedded: Boolean = false,
    displayHandler: DisplayHandler = NoOpDisplayHandler,
    communicationFacility: JupyterCommunicationFacility = CommunicationFacilityMock,
    debugPort: Int? = null,
    inMemoryReplResultsHolder: InMemoryReplResultsHolder = NoOpInMemoryReplResultsHolder,
): ReplForJupyter {
    val componentsProvider =
        object : ReplComponentsProviderBase() {
            override fun provideResolutionInfoProvider() = resolutionInfoProvider

            override fun provideScriptClasspath() = scriptClasspath

            override fun provideHomeDir() = homeDir

            override fun provideMavenRepositories() = mavenRepositories

            override fun provideLibraryResolver() = libraryResolver

            override fun provideRuntimeProperties() = runtimeProperties

            override fun provideScriptReceivers() = scriptReceivers

            override fun provideIsEmbedded() = isEmbedded

            override fun provideDisplayHandler() = displayHandler

            override fun provideCommunicationFacility(): JupyterCommunicationFacility = communicationFacility

            override fun provideDebugPort(): Int? = debugPort

            override fun provideHttpClient() = httpUtil.httpClient

            override fun provideLibraryDescriptorsManager() = httpUtil.libraryDescriptorsManager

            override fun provideLibraryInfoCache() = httpUtil.libraryInfoCache

            override fun provideLibraryReferenceParser() = httpUtil.libraryReferenceParser

            override fun provideInMemoryReplResultsHolder() = inMemoryReplResultsHolder
        }
    return componentsProvider.createRepl()
}

fun ReplComponentsProvider.createRepl() = ReplFactoryBase(this).createRepl()
