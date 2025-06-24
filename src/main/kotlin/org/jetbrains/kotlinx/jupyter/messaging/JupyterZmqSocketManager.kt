package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.protocol.JupyterCallbackBasedSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.callbackBased
import org.jetbrains.kotlinx.jupyter.protocol.openServerZmqSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.zeromq.ZMQ
import java.io.Closeable
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JupyterZmqSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    private val config: KernelConfig,
    private val terminationTimeout: Duration = 15.seconds,
) : JupyterSocketManager, Closeable {
    private val logger = loggerFactory.getLogger(this::class)

    private val zmqContext: ZMQ.Context = ZMQ.context(1)

    private val socketList = mutableListOf<Closeable>()

    private fun openSocket(socketInfo: JupyterZmqSocketInfo): JupyterZmqSocket {
        return openServerZmqSocket(loggerFactory, socketInfo, zmqContext, config)
            .also { socketList.add(it) }
    }

    private val heartbeat = openSocket(JupyterZmqSocketInfo.HB)

    override val sockets: JupyterServerImplSockets

    val shellSocket = openSocket(JupyterZmqSocketInfo.SHELL).callbackBased(logger)
    val controlSocket = openSocket(JupyterZmqSocketInfo.CONTROL).callbackBased(logger)

    init {
        sockets = object : JupyterServerImplSockets {
            override val shell: JupyterCallbackBasedSocket = shellSocket
            override val control: JupyterCallbackBasedSocket = controlSocket
            override val stdin = openSocket(JupyterZmqSocketInfo.STDIN)
            override val iopub = openSocket(JupyterZmqSocketInfo.IOPUB)
        }
    }

    override fun listen() {
        val mainThread = Thread.currentThread()

        fun socketLoop(
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
                }
            }
        }

        val controlThread = thread {
            socketLoop("Control: Interrupted", mainThread) {
                controlSocket.receiveMessageAndRunCallbacks()
            }
        }

        val hbThread = thread {
            socketLoop("Heartbeat: Interrupted", mainThread) {
                heartbeat.let { it.zmqSocket.send(it.zmqSocket.recv()) }
            }
        }

        socketLoop("Main: Interrupted", controlThread, hbThread) {
            shellSocket.receiveMessageAndRunCallbacks()
        }

        try {
            controlThread.join()
            hbThread.join()
        } catch (_: InterruptedException) {
        }
    }

    override fun close() {
        closeWithTimeout(terminationTimeout.inWholeMilliseconds, ::doClose)
    }

    private fun doClose() {
        socketList.forEach { it.close() }
        zmqContext.close()
    }
}
