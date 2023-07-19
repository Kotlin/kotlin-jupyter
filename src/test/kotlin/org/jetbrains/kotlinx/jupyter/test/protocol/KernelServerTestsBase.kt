package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageContent
import org.jetbrains.kotlinx.jupyter.messaging.MessageData
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.StatusReply
import org.jetbrains.kotlinx.jupyter.messaging.makeHeader
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.SocketWrapper
import org.jetbrains.kotlinx.jupyter.protocol.createSocket
import org.jetbrains.kotlinx.jupyter.sendMessage
import org.jetbrains.kotlinx.jupyter.startup.createKotlinKernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createRandomKernelPorts
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.zeromq.ZMQ
import java.io.File
import java.util.*

abstract class KernelServerTestsBase {
    protected abstract val context: ZMQ.Context

    protected val kernelConfig = createKotlinKernelConfig(
        ports = createRandomKernelPorts(),
        signatureKey = "",
        scriptClasspath = classpath,
        homeDir = File(""),
    )

    private val sessionId = UUID.randomUUID().toString()
    private val messageId = listOf(byteArrayOf(1))

    // Set to false to debug kernel execution
    protected val runInSeparateProcess = true
    private val executor = if (runInSeparateProcess) ProcessServerTestExecutor() else ThreadServerTestExecutor()

    open fun beforeEach() {}
    open fun afterEach() {}

    @BeforeEach
    fun setupServer(testInfo: TestInfo) {
        executor.setUp(testInfo, kernelConfig)
        beforeEach()
    }

    @AfterEach
    fun teardownServer() {
        afterEach()
        executor.tearDown()
    }

    fun createClientSocket(socketInfo: JupyterSocketInfo) = createSocket(socketInfo, context, kernelConfig, JupyterSocketSide.CLIENT)

    fun JupyterSocket.sendMessage(msgType: MessageType, content: MessageContent?) {
        sendMessage(Message(id = messageId, MessageData(header = makeHeader(msgType, sessionId = sessionId), content = content)))
    }

    fun JupyterSocket.receiveMessage() = receiveRawMessage()!!.toMessage()

    fun JupyterSocket.receiveStatusReply(): StatusReply {
        (this as? SocketWrapper)?.name shouldBe JupyterSocketInfo.IOPUB.name
        receiveMessage().apply {
            return content.shouldBeTypeOf()
        }
    }

    inline fun JupyterSocket.wrapActionInBusyIdleStatusChange(action: () -> Unit) {
        receiveStatusReply().status shouldBe KernelStatus.BUSY
        action()
        receiveStatusReply().status shouldBe KernelStatus.IDLE
    }
}
