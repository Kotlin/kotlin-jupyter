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
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import java.io.File

interface ReplComponentsProvider {
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
}
