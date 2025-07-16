package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.callbackBased
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.slf4j.Logger
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import zmq.ZError
import java.io.Closeable
import kotlin.concurrent.thread

class JupyterZmqClientSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    side: JupyterSocketSide = JupyterSocketSide.CLIENT,
) : JupyterClientSocketManager {
    private val delegate = JupyterZmqClientReceiveSocketManager(loggerFactory, side)

    override fun open(configParams: KernelJupyterParams): JupyterZmqClientSockets =
        JupyterZmqClientSockets(delegate.open(configParams), loggerFactory)
}

class JupyterZmqClientSockets internal constructor(
    private val delegate: JupyterZmqClientReceiveSockets,
    loggerFactory: KernelLoggerFactory,
) : JupyterClientSockets {
    private val logger = loggerFactory.getLogger(this::class)

    val context: ZMQ.Context get() = delegate.context

    override val shell = delegate.shell.callbackBased(logger)
    override val control = delegate.control.callbackBased(logger)
    override val stdin = delegate.stdin.callbackBased(logger)

    // sending to iopub does not make sense, but JupyterCallbackBasedSocket interface requires it
    override val ioPub = delegate.ioPub.noOpSend().callbackBased(logger)

    private val mainThread =
        thread(name = "JupyterZmqClientSockets.main") {
            val mainThread = Thread.currentThread()
            val socketThreads =
                listOf(::shell, ::control, ::stdin, ::ioPub).map { socketProp ->
                    val socket = socketProp.get()
                    thread(name = "JupyterZmqClientSockets.${socketProp.name}") {
                        socketLoop(parentThread = mainThread, socketName = socketProp.name, logger) {
                            socket.receiveMessageAndRunCallbacks()
                        }
                    }
                }
            try {
                socketThreads.forEach { it.join() }
            } catch (_: InterruptedException) {
                socketThreads.forEach { it.interrupt() }
                socketThreads.forEach {
                    try {
                        it.join()
                    } catch (_: InterruptedException) {
                        // skip
                    }
                }
            }
        }

    override fun close() {
        mergeExceptions {
            // we have to close the sockets ourselves instead of just calling `delegate.close()`,
            // because these are wrappers that store callbacks. calling `socket.close()` deletes the callbacks,
            // preventing leaks.
            for (socket in listOf(shell, control, ioPub, stdin)) {
                catchIndependently { socket.close() }
            }
            catchIndependently { delegate.close() }
        }
        mainThread.interrupt()
        try {
            mainThread.join()
        } catch (_: InterruptedException) {
            // skip
        }
    }

    /** Such socket ignores attempts to send messages */
    private fun JupyterZmqSocket.noOpSend(): NoOpSendSocket = NoOpSendSocket(this)

    /** Such socket ignores attempts to send messages */
    private class NoOpSendSocket(
        private val receiveSocket: JupyterZmqSocket,
    ) : JupyterReceiveSocket by receiveSocket,
        Closeable by receiveSocket,
        JupyterSendReceiveSocket {
        override fun sendRawMessage(msg: RawMessage) {}
    }
}

private inline fun socketLoop(
    parentThread: Thread,
    socketName: String,
    logger: Logger,
    loopBody: () -> Unit,
) {
    while (true) {
        try {
            loopBody()
        } catch (e: Throwable) {
            when (e) {
                is InterruptedException -> {
                    parentThread.interrupt()
                    break
                }

                is ZMQException if e.errorCode == ZError.ECANCELED -> {
                    parentThread.interrupt()
                    break
                }

                else -> logger.error("Exception in socket loop for '$socketName' socket", e)
            }
        }
    }
}
