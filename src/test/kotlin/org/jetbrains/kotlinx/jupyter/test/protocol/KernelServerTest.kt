package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.api.type
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
import java.util.concurrent.ArrayBlockingQueue

@Execution(ExecutionMode.SAME_THREAD)
class KernelServerTest : KernelServerTestsBase(runServerInSeparateProcess = true) {
    private val context: ZMQ.Context = ZMQ.context(1)
    private val hmac by lazy { kernelConfig.jupyterParams.createHmac() }
    private val identity: ByteArray = generateZmqIdentity()

    override fun afterEach() {
        context.term()
        super.afterEach()
    }

    @Test
    fun testHeartbeat() {
        useByteSocket(JupyterZmqSocketInfo.HB) { socket ->
            socket.sendBytes(listOf(ZmqString.getBytes("abc")))
            val msg = ZmqString.getString(socket.receiveBytes().single())
            msg shouldBe "abc"
        }
    }

    @Test
    fun `test control socket`() {
        useMessageSocket(JupyterZmqSocketInfo.CONTROL) { socket ->
            socket.sendMessage(MessageType.INTERRUPT_REQUEST, null)
            val msg = socket.receiveRawMessage()
            msg.type shouldBe MessageType.INTERRUPT_REPLY.type
        }
    }

    @Suppress("SameParameterValue")
    private fun useByteSocket(
        socketInfo: JupyterZmqSocketInfo,
        action: (ByteSocket) -> Unit,
    ) {
        useZmqSocket(socketInfo) { zmqSocket ->
            action(ByteSocket(zmqSocket))
        }
    }

    private class ByteSocket(
        private val zmqSocket: JupyterZmqSocket,
    ) {
        val queue = ArrayBlockingQueue<List<ByteArray>>(10)

        init {
            zmqSocket.onBytesReceived { queue.put(it) }
        }

        fun sendBytes(bytes: List<ByteArray>): Unit = zmqSocket.sendBytes(bytes)

        fun receiveBytes(): List<ByteArray> = queue.take()
    }

    @Suppress("SameParameterValue")
    private fun useMessageSocket(
        socketInfo: JupyterZmqSocketInfo,
        action: (JupyterSendReceiveSocket) -> Unit,
    ) = useZmqSocket(socketInfo) { action(it.sendReceive()) }

    private fun useZmqSocket(
        socketInfo: JupyterZmqSocketInfo,
        action: (JupyterZmqSocket) -> Unit,
    ) {
        createZmqSocket(
            loggerFactory = testLoggerFactory,
            socketInfo = socketInfo,
            context = context,
            configParams = kernelConfig.jupyterParams,
            side = JupyterSocketSide.CLIENT,
            hmac = hmac,
            identity = identity,
        ).use { zmqSocket ->
            zmqSocket.connect()
            action(zmqSocket)
        }
    }
}
