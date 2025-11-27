package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqKernelPorts
import java.io.Closeable

class JupyterZmqServerRunner : JupyterServerRunner {
    override fun tryDeserializePorts(json: JsonObject): KernelPorts? = ZmqKernelPorts.tryDeserialize(json)

    override fun canRun(ports: KernelPorts): Boolean = ports is ZmqKernelPorts

    override fun start(
        jupyterParams: KernelJupyterParams,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Unit,
        registerCloseable: (Closeable) -> Unit,
    ) {
        val sockets = JupyterZmqServerImplSockets(loggerFactory, jupyterParams)
        registerCloseable(sockets)

        setup(sockets)

        sockets.heartbeat.let { hb ->
            hb.onBytesReceived(hb::sendBytes)
        }

        if (!sockets.tryBindAll()) {
            error("Could not bind all sockets")
        }
    }
}
