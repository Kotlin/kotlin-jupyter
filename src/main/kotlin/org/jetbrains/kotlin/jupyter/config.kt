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

data class TypeRenderer(val className: String, val displayCode: String?, val resultCode: String?)

class ArtifactResolution(val artifacts: List<String>, val imports: List<String>, val initCodes: List<String>, val renderers: List<TypeRenderer>)

data class ResolverConfig(val repositories: List<RepositoryCoordinates>, val libraries: Map<String, ArtifactResolution>)

data class KernelConfig(
        val ports: Array<Int>,
        val transport: String,
        val signatureScheme: String,
        val signatureKey: String,
        val pollingIntervalMillis: Long = 100,
        val scriptClasspath: List<File> = emptyList(),
        val resolverConfig: ResolverConfig?
)

val protocolVersion = "5.3"
