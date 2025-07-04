
package org.jetbrains.kotlinx.jupyter.test.protocol

import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.type
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createHmac
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createZmqSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.zeromq.ZMQ

@Execution(ExecutionMode.SAME_THREAD)
class KernelServerTest : KernelServerTestsBase(runServerInSeparateProcess = true) {
    private val context: ZMQ.Context = ZMQ.context(1)
    private val hmac by lazy { kernelConfig.jupyterParams.createHmac() }

    private fun connectClientSocket(socketInfo: JupyterZmqSocketInfo) =
        createZmqSocket(
            testLoggerFactory,
            socketInfo,
            context,
            kernelConfig.jupyterParams,
            JupyterSocketSide.CLIENT,
            hmac,
        ).apply { connect() }

    @Test
    fun testHeartbeat() {
        with(connectClientSocket(JupyterZmqSocketInfo.HB)) {
            tryFinally(
                action = {
                    zmqSocket.send("abc")
                    val msg = zmqSocket.recvString()
                    assertEquals("abc", msg)
                },
                finally = {
                    close()
                    context.term()
                },
            )
        }
    }

    @Test
    fun testShell() {
        with(connectClientSocket(JupyterZmqSocketInfo.CONTROL)) {
            tryFinally(
                action = {
                    sendMessage(MessageType.INTERRUPT_REQUEST, null)
                    val msg = receiveRawMessage()
                    assertEquals(MessageType.INTERRUPT_REPLY.type, msg?.type)
                },
                finally = {
                    close()
                    context.term()
                },
            )
        }
    }
}
