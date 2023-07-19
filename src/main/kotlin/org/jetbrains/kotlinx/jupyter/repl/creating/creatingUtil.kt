package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

fun createRepl(
    resolutionInfoProvider: ResolutionInfoProvider = EmptyResolutionInfoProvider,
    scriptClasspath: List<File> = emptyList(),
    homeDir: File? = null,
    mavenRepositories: List<RepositoryCoordinates> = listOf(),
    libraryResolver: LibraryResolver? = null,
    runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    scriptReceivers: List<Any> = emptyList(),
    isEmbedded: Boolean = false,
    displayHandler: DisplayHandler = NoOpDisplayHandler,
    connection: JupyterConnectionInternal = MockJupyterConnection,
    debugPort: Int? = null,
): ReplForJupyter {
    val factory = object : BaseReplFactory() {
        override fun provideResolutionInfoProvider() = resolutionInfoProvider
        override fun provideScriptClasspath() = scriptClasspath
        override fun provideHomeDir() = homeDir
        override fun provideMavenRepositories() = mavenRepositories
        override fun provideLibraryResolver() = libraryResolver
        override fun provideRuntimeProperties() = runtimeProperties
        override fun provideScriptReceivers() = scriptReceivers
        override fun provideIsEmbedded() = isEmbedded
        override fun provideDisplayHandler() = displayHandler
        override fun provideConnection() = connection
        override fun provideDebugPort(): Int? = debugPort
    }
    return factory.createRepl()
}
