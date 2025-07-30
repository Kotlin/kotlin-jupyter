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
     * Opens sockets, runs [setup] and then runs the server, blocking the thread.
     * The server is stopped when one of the callbacks (see [JupyterServerImplSockets]) throws [InterruptedException].
     * Closable resources returned from [setup] are closed on shutdown.
     */
    fun run(
        jupyterParams: KernelJupyterParams,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Iterable<Closeable>,
    )

    companion object {
        val instances: Iterable<JupyterServerRunner> get() =
            ServiceLoader.load(JupyterServerRunner::class.java)
    }
}
