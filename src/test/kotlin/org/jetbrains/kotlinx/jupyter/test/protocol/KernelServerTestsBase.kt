package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.messaging.CommCloseMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommMsgMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommOpenMessage
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
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.comms.CommCommunicationFacility
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
    private val zmqIdentities = listOf(byteArrayOf(1))

    private val executor = if (runServerInSeparateProcess) ProcessServerTestExecutor() else ThreadServerTestExecutor()

    // Current JUnit test display name to be used by subclasses (e.g., for diagnostics file naming)
    protected lateinit var currentTestDisplayName: String

    open fun beforeEach() {}

    open fun afterEach() {}

    @BeforeEach
    fun setupServer(testInfo: TestInfo) {
        currentTestDisplayName = testInfo.displayName
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
        metadata: JsonElement? = null,
        buffers: List<ByteArray> = emptyList(),
    ) {
        sendMessage(
            Message(
                zmqIdentities = zmqIdentities,
                data =
                    MessageData(
                        header = makeHeader(msgType, sessionId = sessionId),
                        content = content,
                        metadata = metadata,
                    ),
                buffers = buffers,
            ),
        )
    }

    fun JupyterReceiveSocket.receiveMessage() = receiveRawMessage().toMessage()

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

    protected inner class ClientCommCommunicationFacility(
        private val shellSocket: JupyterSendSocket,
    ) : CommCommunicationFacility {
        override val contextMessage: RawMessage? get() = null

        override fun sendCommOpen(
            commId: String,
            targetName: String,
            data: JsonObject,
            metadata: JsonElement?,
            buffers: List<ByteArray>,
        ) {
            shellSocket.sendMessage(
                MessageType.COMM_OPEN,
                CommOpenMessage(commId, targetName, data),
                metadata,
                buffers,
            )
        }

        override fun sendCommMessage(
            commId: String,
            data: JsonObject,
            metadata: JsonElement?,
            buffers: List<ByteArray>,
        ) {
            shellSocket.sendMessage(
                MessageType.COMM_MSG,
                CommMsgMessage(commId, data),
                metadata,
                buffers,
            )
        }

        override fun sendCommClose(
            commId: String,
            data: JsonObject,
            metadata: JsonElement?,
            buffers: List<ByteArray>,
        ) {
            shellSocket.sendMessage(
                MessageType.COMM_CLOSE,
                CommCloseMessage(commId, data),
                metadata,
                buffers,
            )
        }

        override fun processCallbacks(action: () -> Unit) {
            action()
        }
    }
}
