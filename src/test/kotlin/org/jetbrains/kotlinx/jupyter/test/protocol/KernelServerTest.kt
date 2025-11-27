
package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.type
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.sendReceive
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocket
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqSocketInfo
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqString
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createHmac
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createZmqSocket
import org.jetbrains.kotlinx.jupyter.zmq.protocol.generateZmqIdentity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.zeromq.ZMQ
import java.util.concurrent.CompletableFuture

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
        withBytesSocket(JupyterZmqSocketInfo.HB) {
            val replyFuture = CompletableFuture<String>()
            onBytesReceived { replyFuture.complete(ZmqString.getString(it.single())) }
            sendBytes(listOf(ZmqString.getBytes("abc")))
            val msg = replyFuture.get()
            msg shouldBe "abc"
        }
    }

    @Test
    fun `test control socket`() {
        withMessagesSocket(JupyterZmqSocketInfo.CONTROL) {
            sendMessage(MessageType.INTERRUPT_REQUEST, null)
            val msg = receiveRawMessage()
            msg.type shouldBe MessageType.INTERRUPT_REPLY.type
        }
    }

    private fun withBytesSocket(
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

    @Suppress("SameParameterValue")
    private fun withMessagesSocket(
        socketInfo: JupyterZmqSocketInfo,
        action: JupyterSendReceiveSocket.() -> Unit,
    ) = withBytesSocket(socketInfo) { sendReceive().action() }
}
