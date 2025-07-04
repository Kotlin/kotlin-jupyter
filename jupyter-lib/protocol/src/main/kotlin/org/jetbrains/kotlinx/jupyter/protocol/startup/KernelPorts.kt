package org.jetbrains.kotlinx.jupyter.protocol.startup

import kotlinx.serialization.json.JsonPrimitive

interface KernelPorts {
    /**
     * Returns JSON fields to be serialized into the config file (see [KernelJupyterParams]).
     * Needs to be symmetric with [org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner.tryDeserializePorts] implementation.
     */
    fun serialize(): Map<String, JsonPrimitive>
}
