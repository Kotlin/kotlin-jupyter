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
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import java.io.File

abstract class LazilyConstructibleReplComponentsProvider : ReplComponentsProvider {
    override val loggerFactory: KernelLoggerFactory by lazy { provideLoggerFactory() }

    protected abstract fun provideLoggerFactory(): KernelLoggerFactory

    override val resolutionInfoProvider by lazy { provideResolutionInfoProvider() }

    protected abstract fun provideResolutionInfoProvider(): ResolutionInfoProvider

    override val displayHandler by lazy { provideDisplayHandler() }

    protected abstract fun provideDisplayHandler(): DisplayHandler

    override val notebook by lazy { provideNotebook() }

    protected abstract fun provideNotebook(): MutableNotebook

    override val scriptClasspath: List<File> by lazy { provideScriptClasspath() }

    protected abstract fun provideScriptClasspath(): List<File>

    override val homeDir: File? by lazy { provideHomeDir() }

    protected abstract fun provideHomeDir(): File?

    override val debugPort: Int? by lazy { provideDebugPort() }

    protected abstract fun provideDebugPort(): Int?

    override val mavenRepositories: List<MavenRepositoryCoordinates> by lazy { provideMavenRepositories() }

    protected abstract fun provideMavenRepositories(): List<MavenRepositoryCoordinates>

    override val libraryResolver: LibraryResolver? by lazy { provideLibraryResolver() }

    protected abstract fun provideLibraryResolver(): LibraryResolver?

    override val runtimeProperties: ReplRuntimeProperties by lazy { provideRuntimeProperties() }

    protected abstract fun provideRuntimeProperties(): ReplRuntimeProperties

    override val scriptReceivers: List<Any> by lazy { provideScriptReceivers() }

    protected abstract fun provideScriptReceivers(): List<Any>

    override val isEmbedded: Boolean by lazy { provideIsEmbedded() }

    protected abstract fun provideIsEmbedded(): Boolean

    override val librariesScanner: LibrariesScanner by lazy { provideLibrariesScanner() }

    protected abstract fun provideLibrariesScanner(): LibrariesScanner

    override val communicationFacility: JupyterCommunicationFacility by lazy { provideCommunicationFacility() }

    protected abstract fun provideCommunicationFacility(): JupyterCommunicationFacility

    override val commManager: CommManager by lazy { provideCommManager() }

    protected abstract fun provideCommManager(): CommManager

    override val commHandlers: List<CommHandler> by lazy { provideCommHandlers() }

    protected abstract fun provideCommHandlers(): List<CommHandler>

    override val explicitClientType: JupyterClientType? by lazy { provideExplicitClientType() }

    protected abstract fun provideExplicitClientType(): JupyterClientType?

    override val httpClient: HttpClient by lazy { provideHttpClient() }

    protected abstract fun provideHttpClient(): HttpClient

    override val libraryDescriptorsManager: LibraryDescriptorsManager by lazy { provideLibraryDescriptorsManager() }

    protected abstract fun provideLibraryDescriptorsManager(): LibraryDescriptorsManager

    override val libraryInfoCache: LibraryInfoCache by lazy { provideLibraryInfoCache() }

    protected abstract fun provideLibraryInfoCache(): LibraryInfoCache

    override val libraryInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch> by lazy { provideLibraryInfoSwitcher() }

    protected abstract fun provideLibraryInfoSwitcher(): ResolutionInfoSwitcher<DefaultInfoSwitch>

    override val librariesProcessor: LibrariesProcessor by lazy { provideLibrariesProcessor() }

    protected abstract fun provideLibrariesProcessor(): LibrariesProcessor

    override val replOptions: ReplOptions by lazy { provideReplOptions() }

    protected abstract fun provideReplOptions(): ReplOptions

    override val sessionOptions: SessionOptions by lazy { provideSessionOptions() }

    protected abstract fun provideSessionOptions(): SessionOptions

    override val magicsHandler: LibrariesAwareMagicsHandler? by lazy { provideMagicsHandler() }

    protected abstract fun provideMagicsHandler(): LibrariesAwareMagicsHandler?

    override val libraryReferenceParser: LibraryReferenceParser by lazy { provideLibraryReferenceParser() }

    protected abstract fun provideLibraryReferenceParser(): LibraryReferenceParser

    // TODO: add other methods incl. display handler and socket messages listener
    // Inheritors should be constructed of connection (JupyterConnection)
}
