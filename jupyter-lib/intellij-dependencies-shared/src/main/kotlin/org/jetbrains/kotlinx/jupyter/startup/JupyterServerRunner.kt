package org.jetbrains.kotlinx.jupyter.startup

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import java.io.Closeable
import java.util.ServiceLoader

interface JupyterServerRunner {
    /**
     * Tries to deserialize appropriate for this runner [org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts]
     * from the JSON fields from the config file (see [org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams]).
     * Needs to be symmetric with [org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts.serialize] implementation of the result.
     * Returns null if the ports supported by this runner cannot be deserialized from the given JSON fields.
     */
    fun tryDeserializePorts(json: JsonObject): KernelPorts?

    /** Checks whether this runner can run the server on the given [ports]. */
    fun canRun(ports: KernelPorts): Boolean

    /**
     * Opens sockets, runs [setup] and then starts the server, not blocking the thread.
     * Closable resources passed into [registerCloseable] have to be closed on shutdown,
     * in the reverse order of registration. Closing these resources should also stop the server.
     */
    fun start(
        jupyterParams: KernelJupyterParams,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Unit,
        registerCloseable: (Closeable) -> Unit,
    )

    companion object {
        val instances: Iterable<JupyterServerRunner> get() =
            ServiceLoader.load(JupyterServerRunner::class.java)
    }
}
