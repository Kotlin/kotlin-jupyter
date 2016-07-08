package org.jetbrains.kotlin.jupyter

import org.slf4j.LoggerFactory
import java.io.File

internal val log by lazy { LoggerFactory.getLogger("ikotlin") }

enum class JupyterSockets {
    hb,
    shell,
    control,
    stdin,
    iopub
}

data class KernelConfig(
        val ports: Array<Int>,
        val transport: String,
        val signatureScheme: String,
        val signatureKey: String,
        val pollingIntervalMillis: Long = 100,
        val classpath: List<File> = emptyList()
)

val protocolVersion = "5.0"
