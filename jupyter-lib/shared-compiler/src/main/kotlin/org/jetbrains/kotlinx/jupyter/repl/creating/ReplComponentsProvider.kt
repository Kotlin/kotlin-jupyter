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
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import java.io.File

interface ReplComponentsProvider {
    val loggerFactory: KernelLoggerFactory
    val resolutionInfoProvider: ResolutionInfoProvider
    val displayHandler: DisplayHandler
    val notebook: MutableNotebook
    val scriptClasspath: List<File>
    val homeDir: File?
    val debugPort: Int?
    val mavenRepositories: List<MavenRepositoryCoordinates>
    val libraryResolver: LibraryResolver?
    val runtimeProperties: ReplRuntimeProperties
    val scriptReceivers: List<Any>
    val isEmbedded: Boolean
    val librariesScanner: LibrariesScanner
    val communicationFacility: JupyterCommunicationFacility
    val commManager: CommManager
    val commHandlers: List<CommHandler>
    val explicitClientType: JupyterClientType?
    val httpClient: HttpClient
    val libraryDescriptorsManager: LibraryDescriptorsManager
    val libraryInfoCache: LibraryInfoCache
    val libraryInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch>
    val librariesProcessor: LibrariesProcessor
    val replOptions: ReplOptions
    val sessionOptions: SessionOptions
    val magicsHandler: LibrariesAwareMagicsHandler?
    val libraryReferenceParser: LibraryReferenceParser
    val inMemoryReplResultsHolder: InMemoryReplResultsHolder
}
