package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqKernelPorts
import java.io.Closeable
import kotlin.time.Duration.Companion.seconds

class JupyterZmqServerRunner : JupyterServerRunner {
    override fun tryDeserializePorts(json: JsonObject): KernelPorts? = ZmqKernelPorts.tryDeserialize(json)

    override fun canRun(ports: KernelPorts): Boolean = ports is ZmqKernelPorts

    override fun run(
        jupyterParams: KernelJupyterParams,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Iterable<Closeable>,
    ) {
        val sockets = JupyterZmqServerImplSockets(loggerFactory, jupyterParams)

        val closeables = setup(sockets)
        tryFinally(
            action = {
                sockets.heartbeat.let { hb ->
                    hb.onBytesReceived(hb::sendBytes)
                }
                sockets.bindAndJoinAll()
            },
            finally = {
                closeWithTimeout(
                    timeoutMs = 15.seconds.inWholeMilliseconds,
                    doClose = {
                        mergeExceptions {
                            for (closeable in closeables) {
                                catchIndependently { closeable.close() }
                            }
                            catchIndependently { sockets.close() }
                        }
                    },
                )
            },
        )
    }
}
