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

data class ArtifactVariable(val name: String?, val value: String?)

class LibraryDefinition(val artifacts: List<String>, val variables: List<ArtifactVariable>, val imports: List<String>, val initCodes: List<String>, val renderers: List<TypeRenderer>)

data class ResolverConfig(val repositories: List<RepositoryCoordinates>, val libraries: Map<String, LibraryDefinition>)

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
