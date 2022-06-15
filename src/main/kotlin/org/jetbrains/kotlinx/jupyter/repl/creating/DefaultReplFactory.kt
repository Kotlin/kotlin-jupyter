package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.JupyterConnection
import org.jetbrains.kotlinx.jupyter.KernelConfig
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.SocketDisplayHandler
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

class DefaultReplFactory(
    private val _kernelConfig: KernelConfig,
    private val _runtimeProperties: ReplRuntimeProperties,
    private val _scriptReceivers: List<Any>,
    private val _connection: JupyterConnection
) : BaseReplFactory() {
    override fun provideResolutionInfoProvider(): ResolutionInfoProvider {
        return _kernelConfig.resolutionInfoProvider
    }

    override fun provideDisplayHandler(): DisplayHandler {
        return SocketDisplayHandler(_connection, notebook)
    }

    override fun provideScriptClasspath(): List<File> {
        return _kernelConfig.scriptClasspath
    }

    override fun provideHomeDir(): File? {
        return _kernelConfig.homeDir
    }

    override fun provideMavenRepositories(): List<RepositoryCoordinates> {
        return _kernelConfig.mavenRepositories
    }

    override fun provideLibraryResolver(): LibraryResolver? {
        return _kernelConfig.libraryResolver
    }

    override fun provideRuntimeProperties(): ReplRuntimeProperties {
        return _runtimeProperties
    }

    override fun provideScriptReceivers(): List<Any> {
        return _scriptReceivers
    }

    override fun provideIsEmbedded(): Boolean {
        return _kernelConfig.embedded
    }
}
