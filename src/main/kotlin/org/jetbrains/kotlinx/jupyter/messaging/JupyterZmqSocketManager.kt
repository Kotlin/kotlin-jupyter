package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.portField
import org.jetbrains.kotlinx.jupyter.exceptions.mergeExceptions
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.callbackBased
import org.jetbrains.kotlinx.jupyter.protocol.openServerZmqSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.ZmqKernelPorts
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.slf4j.Logger
import org.zeromq.ZMQ
import java.io.Closeable
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

class JupyterZmqServerRunner : JupyterServerRunner {
    override fun tryDeserializePorts(jsonFields: Map<String, JsonPrimitive>): KernelPorts? {
        return ZmqKernelPorts { socket ->
            val fieldName = socket.portField
            jsonFields[fieldName]?.let { Json.decodeFromJsonElement<Int>(it) }
                ?: return null
        }
    }

    override fun canRun(ports: KernelPorts): Boolean = ports is ZmqKernelPorts

    override fun run(
        config: KernelConfig,
        loggerFactory: KernelLoggerFactory,
        setup: (JupyterServerImplSockets) -> Iterable<Closeable>,
    ) {
        val logger = loggerFactory.getLogger(this::class)
        val zmqContext: ZMQ.Context = ZMQ.context(1)
        val socketList = mutableListOf<Closeable>()

        fun openSocket(socketInfo: JupyterZmqSocketInfo): JupyterZmqSocket {
            return openServerZmqSocket(loggerFactory, socketInfo, zmqContext, config)
                .also { socketList.add(it) }
        }

        val heartbeat = openSocket(JupyterZmqSocketInfo.HB)
        val shellSocket = openSocket(JupyterZmqSocketInfo.SHELL).callbackBased(logger)
        val controlSocket = openSocket(JupyterZmqSocketInfo.CONTROL).callbackBased(logger)

        val sockets = object : JupyterServerImplSockets {
            override val shell: JupyterCallbackBasedSocket = shellSocket
            override val control: JupyterCallbackBasedSocket = controlSocket
            override val stdin = openSocket(JupyterZmqSocketInfo.STDIN)
            override val iopub = openSocket(JupyterZmqSocketInfo.IOPUB)
        }

        val closeables = setup(sockets)
        try {
            val mainThread = Thread.currentThread()

            val controlThread = thread {
                socketLoop(logger, "Control: Interrupted", mainThread) {
                    controlSocket.receiveMessageAndRunCallbacks()
                }
            }

            val hbThread = thread {
                socketLoop(logger, "Heartbeat: Interrupted", mainThread) {
                    heartbeat.let { it.zmqSocket.send(it.zmqSocket.recv()) }
                }
            }

            socketLoop(logger, "Main: Interrupted", controlThread, hbThread) {
                shellSocket.receiveMessageAndRunCallbacks()
            }

            try {
                controlThread.join()
                hbThread.join()
            } catch (_: InterruptedException) {
            }
        } finally {
            closeWithTimeout(
                timeoutMs = 15.seconds.inWholeMilliseconds,
                doClose = {
                    mergeExceptions {
                        for (closeable in closeables) {
                            catchIndependently { closeable.close() }
                        }
                        for (socket in socketList) {
                            catchIndependently { socket.close() }
                        }
                        catchIndependently { zmqContext.close() }
                    }
                },
            )
        }
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
            } catch (_: InterruptedException) {
                logger.debug(interruptedMessage)
                threadsToInterrupt.forEach { it.interrupt() }
                break
            } catch (e: Throwable) {
                logger.error("Error during message processing", e)
            }
        }
    }
}
