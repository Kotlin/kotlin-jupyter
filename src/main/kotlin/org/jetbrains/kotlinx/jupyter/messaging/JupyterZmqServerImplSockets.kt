package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createHmac
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createZmqSocket
import org.jetbrains.kotlinx.jupyter.zmq.protocol.generateZmqIdentity
import org.zeromq.ZMQ
import java.io.Closeable

internal class JupyterZmqServerImplSockets(
    private val loggerFactory: KernelLoggerFactory,
    private val jupyterParams: KernelJupyterParams,
) : JupyterServerImplSockets,
    Closeable {
    private val zmqContext: ZMQ.Context = ZMQ.context(1)
    private val hmac by lazy { jupyterParams.createHmac() }
    private val identity: ByteArray = generateZmqIdentity()

    private val socketsToClose = mutableListOf<Closeable>()
    private val socketsToBind = mutableListOf<JupyterZmqSocket>()

    val heartbeat = createSocket(JupyterZmqSocketInfo.HB)
    override val shell: JupyterZmqSocket = createSocket(JupyterZmqSocketInfo.SHELL)
    override val control: JupyterZmqSocket = createSocket(JupyterZmqSocketInfo.CONTROL)
    override val stdin = createSendReceiveSocket(JupyterZmqSocketInfo.STDIN)

    // we must initialize it last, so it will be the last to bind, this one is the slowest to bind
    // (see ZmqSocketWithCancellationImpl.bind implementation)
    override val iopub = createSocket(JupyterZmqSocketInfo.IOPUB)

    fun bindAndJoinAll() {
        if (socketsToBind.any { !it.tryBind() }) return
        try {
            control.join()
            heartbeat.join()
        } catch (_: InterruptedException) {
        }
    }

    override fun close() {
        mergeExceptions {
            for (socket in socketsToClose) {
                catchIndependently { socket.close() }
            }
            catchIndependently { zmqContext.close() }
        }
    }

    private fun createSocket(socketInfo: JupyterZmqSocketInfo): JupyterZmqSocket =
        createAndBindSocket(socketInfo)
            .also { socketsToClose.add(it) }

    @Suppress("SameParameterValue")
    private fun createSendReceiveSocket(socketInfo: JupyterZmqSocketInfo): JupyterSendReceiveSocket =
        createAndBindSocket(socketInfo)
            .also { socketsToClose.add(it) }
            .sendReceive()

    private fun createAndBindSocket(socketInfo: JupyterZmqSocketInfo): JupyterZmqSocket =
        createZmqSocket(
            loggerFactory = loggerFactory,
            socketInfo = socketInfo,
            context = zmqContext,
            configParams = jupyterParams,
            side = JupyterSocketSide.SERVER,
            hmac = hmac,
            identity = identity,
        ).also { socketsToBind.add(it) }
}
