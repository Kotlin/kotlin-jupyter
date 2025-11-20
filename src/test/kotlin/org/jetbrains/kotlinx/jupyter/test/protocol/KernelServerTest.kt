
package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.type
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqString
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createHmac
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createZmqSocket
import org.jetbrains.kotlinx.jupyter.zmq.protocol.generateZmqIdentity
import org.jetbrains.kotlinx.jupyter.zmq.protocol.recv
import org.jetbrains.kotlinx.jupyter.zmq.protocol.send
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.zeromq.ZMQ

@Execution(ExecutionMode.SAME_THREAD)
class KernelServerTest : KernelServerTestsBase(runServerInSeparateProcess = true) {
    private val context: ZMQ.Context = ZMQ.context(1)
    private val hmac by lazy { kernelConfig.jupyterParams.createHmac() }
    private val identity: ByteArray = generateZmqIdentity()

    private fun connectClientSocket(socketInfo: JupyterZmqSocketInfo) =
        createZmqSocket(
            testLoggerFactory,
            socketInfo,
            context,
            kernelConfig.jupyterParams,
            JupyterSocketSide.CLIENT,
            hmac,
            identity,
        ).apply { connect() }

    @Test
    fun testHeartbeat() {
        withSocketOfType(JupyterZmqSocketInfo.HB) {
            zmqSocket.send(ZmqString.getBytes("abc"))
            val msg = ZmqString.getString(zmqSocket.recv())
            msg shouldBe "abc"
        }
    }

    @Test
    fun `test control socket`() {
        withSocketOfType(JupyterZmqSocketInfo.CONTROL) {
            sendMessage(MessageType.INTERRUPT_REQUEST, null)
            val msg = receiveRawMessage()
            msg.shouldNotBeNull()
            msg.type shouldBe MessageType.INTERRUPT_REPLY.type
        }
    }

    private fun withSocketOfType(
        socketInfo: JupyterZmqSocketInfo,
        action: JupyterZmqSocket.() -> Unit,
    ) {
        with(connectClientSocket(socketInfo)) {
            tryFinally(
                action = {
                    action()
                },
                finally = {
                    close()
                    context.term()
                },
            )
        }
    }
}
