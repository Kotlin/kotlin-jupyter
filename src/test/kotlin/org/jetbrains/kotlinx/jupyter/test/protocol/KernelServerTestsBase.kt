package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageContent
import org.jetbrains.kotlinx.jupyter.messaging.MessageData
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.StatusMessage
import org.jetbrains.kotlinx.jupyter.messaging.makeHeader
import org.jetbrains.kotlinx.jupyter.messaging.sendMessage
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.createKotlinKernelConfig
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createRandomZmqKernelPorts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.util.UUID

/**
 * Base class for tests that need to have fine-tuned control over the kernel server execution.
 *
 * @param runServerInSeparateProcess The debugger only works if the kernel is running in the same process
 * as the test. The default is to run in a separate one (similar to how it would work in
 * production). To enable debugging, set this value to `false`
 */
abstract class KernelServerTestsBase(
    protected val runServerInSeparateProcess: Boolean,
    generatePorts: () -> KernelPorts = ::createRandomZmqKernelPorts,
) {
    protected val kernelConfig =
        createKotlinKernelConfig(
            ports = generatePorts(),
            signatureKey = "test-signature",
            scriptClasspath = classpath,
            homeDir = File(""),
        )

    private val sessionId = UUID.randomUUID().toString()
    private val messageId = listOf(byteArrayOf(1))

    private val executor = if (runServerInSeparateProcess) ProcessServerTestExecutor() else ThreadServerTestExecutor()

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

    fun JupyterSendSocket.sendMessage(
        msgType: MessageType,
        content: MessageContent?,
    ) {
        sendMessage(Message(id = messageId, MessageData(header = makeHeader(msgType, sessionId = sessionId), content = content)))
    }

    fun JupyterReceiveSocket.receiveMessage() = receiveRawMessage()!!.toMessage()

    fun receiveStatusReply(iopubSocket: JupyterReceiveSocket): StatusMessage {
        iopubSocket.receiveMessage().apply {
            return content.shouldBeTypeOf()
        }
    }

    inline fun wrapActionInBusyIdleStatusChange(
        iopubSocket: JupyterReceiveSocket,
        action: () -> Unit,
    ) {
        receiveStatusReply(iopubSocket).status shouldBe KernelStatus.BUSY
        action()
        receiveStatusReply(iopubSocket).status shouldBe KernelStatus.IDLE
    }
}
