package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.config.logger
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.SocketDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.util.toUpperCaseAsciiOnly
import java.io.File

class DefaultReplComponentsProvider(
    private val _settings: DefaultReplSettings,
    private val _communicationFacility: JupyterCommunicationFacility,
    private val _commManager: CommManager,
) : ReplComponentsProviderBase() {
    override fun provideResolutionInfoProvider(): ResolutionInfoProvider {
        return _settings.replConfig.resolutionInfoProvider
    }

    override fun provideDisplayHandler(): DisplayHandler {
        return SocketDisplayHandler(_communicationFacility, notebook)
    }

    override fun provideScriptClasspath(): List<File> {
        return _settings.kernelConfig.scriptClasspath
    }

    override fun provideHomeDir(): File? {
        return _settings.kernelConfig.homeDir
    }

    override fun provideMavenRepositories(): List<MavenRepositoryCoordinates> {
        return _settings.replConfig.mavenRepositories
    }

    override fun provideLibraryResolver(): LibraryResolver? {
        return _settings.replConfig.libraryResolver
    }

    override fun provideRuntimeProperties(): ReplRuntimeProperties {
        return _settings.runtimeProperties
    }

    override fun provideScriptReceivers(): List<Any> {
        return _settings.scriptReceivers
    }

    override fun provideIsEmbedded(): Boolean {
        return _settings.replConfig.embedded
    }

    override fun provideCommunicationFacility(): JupyterCommunicationFacility {
        return _communicationFacility
    }

    override fun provideCommManager(): CommManager {
        return _commManager
    }

    override fun provideDebugPort(): Int? {
        return _settings.kernelConfig.debugPort
    }

    override fun provideExplicitClientType(): JupyterClientType? {
        return _settings.kernelConfig.clientType?.let { typeName ->
            try {
                JupyterClientType.valueOf(typeName.toUpperCaseAsciiOnly())
            } catch (e: IllegalArgumentException) {
                LOG.warn("Unknown client type: $typeName")
                null
            }
        }
    }

    companion object {
        private val LOG = logger<DefaultReplComponentsProvider>()
    }
}
