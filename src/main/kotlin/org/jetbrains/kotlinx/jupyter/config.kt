package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.defaultRepositories
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.config.readResourceAsIniFile
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

const val protocolVersion = "5.3"

internal val log by lazy { getLogger() }

val defaultRepositoriesCoordinates = defaultRepositories.map { RepositoryCoordinates(it.path) }

val defaultRuntimeProperties by lazy {
    RuntimeKernelProperties(readResourceAsIniFile("runtime.properties"))
}

fun createRuntimeProperties(
    kernelConfig: KernelConfig,
    defaultProperties: ReplRuntimeProperties = defaultRuntimeProperties,
): ReplRuntimeProperties {
    return object : ReplRuntimeProperties by defaultProperties {
        override val jvmTargetForSnippets: String
            get() = kernelConfig.jvmTargetForSnippets ?: defaultProperties.jvmTargetForSnippets
    }
}

data class OutputConfig(
    var captureOutput: Boolean = true,
    var captureBufferTimeLimitMs: Long = 100,
    var captureBufferMaxSize: Int = 1000,
    var cellOutputMaxSize: Int = 100000,
    var captureNewlineBufferSize: Int = 100,
) {
    fun update(other: OutputConfig) {
        captureOutput = other.captureOutput
        captureBufferTimeLimitMs = other.captureBufferTimeLimitMs
        captureBufferMaxSize = other.captureBufferMaxSize
        cellOutputMaxSize = other.cellOutputMaxSize
        captureNewlineBufferSize = other.captureNewlineBufferSize
    }
}

class RuntimeKernelProperties(val map: Map<String, String>) : ReplRuntimeProperties {
    override val version: KotlinKernelVersion? by lazy {
        map["version"]?.let { KotlinKernelVersion.from(it) }
    }

    @Deprecated("This parameter is meaningless, do not use")
    override val librariesFormatVersion: Int
        get() = throw RuntimeException("Libraries format version is not specified!")
    override val currentBranch: String
        get() = map["currentBranch"] ?: throw RuntimeException("Current branch is not specified!")
    override val currentSha: String
        get() = map["currentSha"] ?: throw RuntimeException("Current commit SHA is not specified!")
    override val jvmTargetForSnippets by lazy {
        map["jvmTargetForSnippets"] ?: JavaRuntime.version
    }
}

data class ReplConfig(
    val mavenRepositories: List<RepositoryCoordinates> = listOf(),
    val libraryResolver: LibraryResolver? = null,
    val resolutionInfoProvider: ResolutionInfoProvider,
    val embedded: Boolean = false,
) {
    companion object {
        fun create(
            resolutionInfoProvider: ResolutionInfoProvider,
            homeDir: File? = null,
            embedded: Boolean = false,
        ): ReplConfig {
            return ReplConfig(
                mavenRepositories = defaultRepositoriesCoordinates,
                libraryResolver = getStandardResolver(homeDir?.toString(), resolutionInfoProvider),
                resolutionInfoProvider = resolutionInfoProvider,
                embedded = embedded,
            )
        }
    }
}
