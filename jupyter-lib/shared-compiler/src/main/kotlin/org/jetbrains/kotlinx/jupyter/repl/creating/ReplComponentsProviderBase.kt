package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.CommunicationFacilityMock
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.DebugPortCommHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl
import java.io.File

abstract class ReplComponentsProviderBase : LazilyConstructibleReplComponentsProvider() {
    override fun provideResolutionInfoProvider(): ResolutionInfoProvider = EmptyResolutionInfoProvider
    override fun provideDisplayHandler(): DisplayHandler = NoOpDisplayHandler
    override fun provideNotebook(): MutableNotebook = NotebookImpl(
        runtimeProperties,
        commManager,
        explicitClientType,
        librariesScanner,
    )

    override fun provideScriptClasspath() = emptyList<File>()
    override fun provideHomeDir(): File? = null
    override fun provideMavenRepositories() = emptyList<MavenRepositoryCoordinates>()
    override fun provideLibraryResolver(): LibraryResolver? = null
    override fun provideRuntimeProperties(): ReplRuntimeProperties = defaultRuntimeProperties
    override fun provideScriptReceivers() = emptyList<Any>()
    override fun provideIsEmbedded() = false
    override fun provideLibrariesScanner(): LibrariesScanner = LibrariesScanner()
    override fun provideCommManager(): CommManager = CommManagerImpl(communicationFacility)
    override fun provideCommHandlers(): List<CommHandler> = listOf(
        DebugPortCommHandler(),
    )

    override fun provideExplicitClientType(): JupyterClientType? = null

    override fun provideCommunicationFacility(): JupyterCommunicationFacility = CommunicationFacilityMock
    override fun provideDebugPort(): Int? = null
}
