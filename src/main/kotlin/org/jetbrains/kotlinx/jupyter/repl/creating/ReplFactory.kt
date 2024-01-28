package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.impl.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import java.io.File

abstract class ReplFactory {
    fun createRepl(): ReplForJupyter {
        return ReplForJupyterImpl(
            resolutionInfoProvider,
            displayHandler,
            scriptClasspath,
            homeDir,
            mavenRepositories,
            libraryResolver,
            runtimeProperties,
            scriptReceivers,
            isEmbedded,
            notebook,
            librariesScanner,
            debugPort,
            commHandlers,
        )
    }

    protected val resolutionInfoProvider by lazy { provideResolutionInfoProvider() }
    protected abstract fun provideResolutionInfoProvider(): ResolutionInfoProvider

    protected val displayHandler by lazy { provideDisplayHandler() }
    protected abstract fun provideDisplayHandler(): DisplayHandler

    protected val notebook by lazy { provideNotebook() }
    protected abstract fun provideNotebook(): MutableNotebook

    protected val scriptClasspath: List<File> by lazy { provideScriptClasspath() }
    protected abstract fun provideScriptClasspath(): List<File>

    protected val homeDir: File? by lazy { provideHomeDir() }
    protected abstract fun provideHomeDir(): File?

    protected val debugPort: Int? by lazy { provideDebugPort() }
    protected abstract fun provideDebugPort(): Int?

    protected val mavenRepositories: List<MavenRepositoryCoordinates> by lazy { provideMavenRepositories() }
    protected abstract fun provideMavenRepositories(): List<MavenRepositoryCoordinates>

    protected val libraryResolver: LibraryResolver? by lazy { provideLibraryResolver() }
    protected abstract fun provideLibraryResolver(): LibraryResolver?

    protected val runtimeProperties: ReplRuntimeProperties by lazy { provideRuntimeProperties() }
    protected abstract fun provideRuntimeProperties(): ReplRuntimeProperties

    protected val scriptReceivers: List<Any> by lazy { provideScriptReceivers() }
    protected abstract fun provideScriptReceivers(): List<Any>

    protected val isEmbedded: Boolean by lazy { provideIsEmbedded() }
    protected abstract fun provideIsEmbedded(): Boolean

    protected val librariesScanner: LibrariesScanner by lazy { provideLibrariesScanner() }
    protected abstract fun provideLibrariesScanner(): LibrariesScanner

    protected val communicationFacility: JupyterCommunicationFacility by lazy { provideCommunicationFacility() }
    protected abstract fun provideCommunicationFacility(): JupyterCommunicationFacility

    protected val commManager: CommManager by lazy { provideCommManager() }
    protected abstract fun provideCommManager(): CommManager

    protected val commHandlers: List<CommHandler> by lazy { provideCommHandlers() }
    protected abstract fun provideCommHandlers(): List<CommHandler>

    protected val explicitClientType: JupyterClientType? by lazy { provideExplicitClientType() }
    protected abstract fun provideExplicitClientType(): JupyterClientType?

    // TODO: add other methods incl. display handler and socket messages listener
    // Inheritors should be constructed of connection (JupyterConnection)
}
