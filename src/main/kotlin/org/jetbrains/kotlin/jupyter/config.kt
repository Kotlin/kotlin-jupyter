package org.jetbrains.kotlin.jupyter

import org.slf4j.LoggerFactory
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

internal val log by lazy { LoggerFactory.getLogger("ikotlin") }

enum class JupyterSockets {
    hb,
    shell,
    control,
    stdin,
    iopub
}

class ArtifactResolution(val coordinates: String, val imports: List<String>)

data class LibrariesConfig(val repositories: List<RepositoryCoordinates>, val artifactsMapping: Map<String, ArtifactResolution>)

data class KernelConfig(
        val ports: Array<Int>,
        val transport: String,
        val signatureScheme: String,
        val signatureKey: String,
        val pollingIntervalMillis: Long = 100,
        val scriptClasspath: List<File> = emptyList(),
        val librariesConfig: LibrariesConfig?
)

val protocolVersion = "5.3"
