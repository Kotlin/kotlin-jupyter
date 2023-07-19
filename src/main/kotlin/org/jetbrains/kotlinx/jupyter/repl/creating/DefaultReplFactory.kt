package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import org.jetbrains.kotlinx.jupyter.ReplConfig
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.SocketDisplayHandler
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

class DefaultReplFactory(
    private val _kernelConfig: KernelConfig,
    private val _replConfig: ReplConfig,
    private val _runtimeProperties: ReplRuntimeProperties,
    private val _scriptReceivers: List<Any>,
    private val _connection: JupyterConnectionInternal,
    private val _commManager: CommManager,
) : BaseReplFactory() {
    override fun provideResolutionInfoProvider(): ResolutionInfoProvider {
        return _replConfig.resolutionInfoProvider
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
        return _replConfig.mavenRepositories
    }

    override fun provideLibraryResolver(): LibraryResolver? {
        return _replConfig.libraryResolver
    }

    override fun provideRuntimeProperties(): ReplRuntimeProperties {
        return _runtimeProperties
    }

    override fun provideScriptReceivers(): List<Any> {
        return _scriptReceivers
    }

    override fun provideIsEmbedded(): Boolean {
        return _replConfig.embedded
    }

    override fun provideConnection(): JupyterConnectionInternal {
        return _connection
    }

    override fun provideCommManager(): CommManager {
        return _commManager
    }

    override fun provideDebugPort(): Int? {
        return _kernelConfig.debugPort
    }

    override fun provideExplicitClientType(): JupyterClientType? {
        return _kernelConfig.clientType?.let { typeName ->
            try {
                JupyterClientType.valueOf(typeName.toUpperCaseAsciiOnly())
            } catch (e: IllegalArgumentException) {
                log.warn("Unknown client type: $typeName")
                null
            }
        }
    }
}
