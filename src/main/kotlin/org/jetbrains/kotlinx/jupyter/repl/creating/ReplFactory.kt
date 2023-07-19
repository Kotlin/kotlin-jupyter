package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.MutableNotebook
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.CommHandler
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

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
        ).also { repl ->
            commHandlers.forEach { handler ->
                repl.notebook.commManager.registerCommTarget(handler.targetId) { comm, _ ->
                    // handler.onReceive(comm, data, repl) // maybe send right away?

                    comm.onMessage {
                        handler.onReceive(comm, it, repl)
                    }
                }
            }
        }
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

    protected val mavenRepositories: List<RepositoryCoordinates> by lazy { provideMavenRepositories() }
    protected abstract fun provideMavenRepositories(): List<RepositoryCoordinates>

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

    protected val connection: JupyterConnectionInternal by lazy { provideConnection() }
    protected abstract fun provideConnection(): JupyterConnectionInternal

    protected val commManager: CommManager by lazy { provideCommManager() }
    protected abstract fun provideCommManager(): CommManager

    protected val commHandlers: List<CommHandler> by lazy { provideCommHandlers() }
    protected abstract fun provideCommHandlers(): List<CommHandler>
    private fun checkCommHandlers() {
        val uniqueTargets = commHandlers.distinctBy { it.targetId }.size
        assert(uniqueTargets == commHandlers.size) {
            val duplicates = commHandlers.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            "Duplicate bundled comm targets found! $duplicates"
        }
    }

    protected val explicitClientType: JupyterClientType? by lazy { provideExplicitClientType() }
    protected abstract fun provideExplicitClientType(): JupyterClientType?

    // TODO: add other methods incl. display handler and socket messages listener
    // Inheritors should be constructed of connection (JupyterConnection)

    init {
        checkCommHandlers()
    }
}
