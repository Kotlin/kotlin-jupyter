package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerImplSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqKernelPorts
import org.slf4j.Logger
import org.zeromq.ZMQException
import zmq.ZError
import java.io.Closeable
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

class JupyterZmqServerRunner : JupyterServerRunner {
    override fun tryDeserializePorts(json: JsonObject): KernelPorts? = ZmqKernelPorts.tryDeserialize(json)

    override fun canRun(ports: KernelPorts): Boolean = ports is ZmqKernelPorts

    override fun run(
        jupyterParams: KernelJupyterParams,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Iterable<Closeable>,
    ) {
        val logger = loggerFactory.getLogger(this::class)
        val sockets = JupyterZmqServerImplSockets(loggerFactory, jupyterParams)

        val closeables = setup(sockets)
        tryFinally(
            action = {
                sockets.bindAll()

                val mainThread = Thread.currentThread()

                val controlThread =
                    thread(name = "JupyterZmqServerRunner.CONTROL") {
                        socketLoop(logger, "Control: Interrupted", mainThread) {
                            sockets.control.receiveMessageAndRunCallbacks()
                        }
                    }

                val hbThread =
                    thread(name = "JupyterZmqServerRunner.HB") {
                        socketLoop(logger, "Heartbeat: Interrupted", mainThread) {
                            sockets.heartbeat.let { it.zmqSocket.send(it.zmqSocket.recv()) }
                        }
                    }

                socketLoop(logger, "Main: Interrupted", controlThread, hbThread) {
                    sockets.shell.receiveMessageAndRunCallbacks()
                }

                try {
                    controlThread.join()
                    hbThread.join()
                } catch (_: InterruptedException) {
                }
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

    private fun socketLoop(
        logger: Logger,
        interruptedMessage: String,
        vararg threadsToInterrupt: Thread,
        loopBody: () -> Unit,
    ) {
        while (true) {
            try {
                loopBody()
            } catch (e: Throwable) {
                when (e) {
                    is InterruptedException -> {
                        logger.debug(interruptedMessage)
                        threadsToInterrupt.forEach { it.interrupt() }
                        break
                    }

                    is ZMQException if e.errorCode == ZError.ECANCELED -> {
                        logger.debug(interruptedMessage)
                        threadsToInterrupt.forEach { it.interrupt() }
                        break
                    }

                    else -> logger.error("Error during message processing", e)
                }
            }
        }
    }
}
