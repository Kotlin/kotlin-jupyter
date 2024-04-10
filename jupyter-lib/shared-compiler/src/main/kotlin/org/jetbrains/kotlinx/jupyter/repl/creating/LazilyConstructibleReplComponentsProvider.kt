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

interface LazilyConstructibleReplComponentsProvider : ReplComponentsProvider {
    fun provideLoggerFactory(): KernelLoggerFactory

    fun provideResolutionInfoProvider(): ResolutionInfoProvider

    fun provideDisplayHandler(): DisplayHandler

    fun provideNotebook(): MutableNotebook

    fun provideScriptClasspath(): List<File>

    fun provideHomeDir(): File?

    fun provideDebugPort(): Int?

    fun provideMavenRepositories(): List<MavenRepositoryCoordinates>

    fun provideLibraryResolver(): LibraryResolver?

    fun provideRuntimeProperties(): ReplRuntimeProperties

    fun provideScriptReceivers(): List<Any>

    fun provideIsEmbedded(): Boolean

    fun provideLibrariesScanner(): LibrariesScanner

    fun provideCommunicationFacility(): JupyterCommunicationFacility

    fun provideCommManager(): CommManager

    fun provideCommHandlers(): List<CommHandler>

    fun provideExplicitClientType(): JupyterClientType?

    fun provideHttpClient(): HttpClient

    fun provideLibraryDescriptorsManager(): LibraryDescriptorsManager

    fun provideLibraryInfoCache(): LibraryInfoCache

    fun provideLibraryInfoSwitcher(): ResolutionInfoSwitcher<DefaultInfoSwitch>

    fun provideLibrariesProcessor(): LibrariesProcessor

    fun provideReplOptions(): ReplOptions

    fun provideSessionOptions(): SessionOptions

    fun provideMagicsHandler(): LibrariesAwareMagicsHandler?

    fun provideLibraryReferenceParser(): LibraryReferenceParser

    fun provideInMemoryReplResultsHolder(): InMemoryReplResultsHolder
}
