package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryInfoCache
import org.jetbrains.kotlinx.jupyter.libraries.LibraryReferenceParser
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import java.io.File

abstract class LazilyConstructibleReplComponentsProviderImpl : LazilyConstructibleReplComponentsProvider {
    override val loggerFactory: KernelLoggerFactory by lazy { provideLoggerFactory() }
    override val resolutionInfoProvider by lazy { provideResolutionInfoProvider() }
    override val displayHandler by lazy { provideDisplayHandler() }
    override val notebook by lazy { provideNotebook() }
    override val scriptClasspath: List<File> by lazy { provideScriptClasspath() }
    override val homeDir: File? by lazy { provideHomeDir() }
    override val debugPort: Int? by lazy { provideDebugPort() }
    override val mavenRepositories: List<MavenRepositoryCoordinates> by lazy { provideMavenRepositories() }
    override val libraryResolver: LibraryResolver? by lazy { provideLibraryResolver() }
    override val runtimeProperties: ReplRuntimeProperties by lazy { provideRuntimeProperties() }
    override val scriptReceivers: List<Any> by lazy { provideScriptReceivers() }
    override val isEmbedded: Boolean by lazy { provideIsEmbedded() }
    override val librariesScanner: LibrariesScanner by lazy { provideLibrariesScanner() }
    override val communicationFacility: JupyterCommunicationFacility by lazy { provideCommunicationFacility() }
    override val commManager: CommManager by lazy { provideCommManager() }
    override val commHandlers: List<CommHandler> by lazy { provideCommHandlers() }
    override val explicitClientType: JupyterClientType? by lazy { provideExplicitClientType() }
    override val httpClient: HttpClient by lazy { provideHttpClient() }
    override val libraryDescriptorsManager: LibraryDescriptorsManager by lazy { provideLibraryDescriptorsManager() }
    override val libraryInfoCache: LibraryInfoCache by lazy { provideLibraryInfoCache() }
    override val libraryInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch> by lazy { provideLibraryInfoSwitcher() }
    override val librariesProcessor: LibrariesProcessor by lazy { provideLibrariesProcessor() }
    override val replOptions: ReplOptions by lazy { provideReplOptions() }
    override val sessionOptions: SessionOptions by lazy { provideSessionOptions() }
    override val magicsHandler: LibrariesAwareMagicsHandler? by lazy { provideMagicsHandler() }
    override val libraryReferenceParser: LibraryReferenceParser by lazy { provideLibraryReferenceParser() }
    override val inMemoryReplResultsHolder: InMemoryReplResultsHolder by lazy { provideInMemoryReplResultsHolder() }

    // TODO: add other methods incl. display handler and socket messages listener
    // Inheritors should be constructed of connection (JupyterConnection)
}
