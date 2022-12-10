package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.ReplConfig
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.kernelServer
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageContent
import org.jetbrains.kotlinx.jupyter.messaging.MessageData
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.StatusReply
import org.jetbrains.kotlinx.jupyter.messaging.makeHeader
import org.jetbrains.kotlinx.jupyter.messaging.toMessage
import org.jetbrains.kotlinx.jupyter.protocol.HMAC
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.SocketWrapper
import org.jetbrains.kotlinx.jupyter.protocol.createSocket
import org.jetbrains.kotlinx.jupyter.protocol.receiveRawMessage
import org.jetbrains.kotlinx.jupyter.sendMessage
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.jetbrains.kotlinx.jupyter.startup.createKotlinKernelConfig
import org.jetbrains.kotlinx.jupyter.startup.javaCmdLine
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.zeromq.ZMQ
import java.io.File
import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

abstract class KernelServerTestsBase {
    protected abstract val context: ZMQ.Context

    protected val kernelConfig = createKotlinKernelConfig(
        ports = createKernelPorts { randomPort() },
        signatureKey = "",
        scriptClasspath = classpath,
        homeDir = File(""),
    )

    private val replConfig = ReplConfig.create(
        EmptyResolutionInfoProvider,
        kernelConfig.homeDir,
    )

    private val sessionId = UUID.randomUUID().toString()

    protected val hmac = HMAC(kernelConfig.signatureScheme, kernelConfig.signatureKey)

    // Set to false to debug kernel execution
    protected val runInSeparateProcess = true
    private var serverProcess: Process? = null
    private var serverThread: Thread? = null

    protected val messageId = listOf(byteArrayOf(1))

    private var testLogger: Logger? = null
    private var fileOut: File? = null
    private var fileErr: File? = null

    open fun beforeEach() {}
    open fun afterEach() {}

    @BeforeEach
    fun setupServer(testInfo: TestInfo) {
        if (runInSeparateProcess) {
            val testName = testInfo.displayName
            val command = kernelConfig.javaCmdLine(javaBin, testName, classpathArg)

            testLogger = LoggerFactory.getLogger("testKernel_$testName")
            fileOut = File.createTempFile("tmp-kernel-out-$testName", ".txt")
            fileErr = File.createTempFile("tmp-kernel-err-$testName", ".txt")

            serverProcess = ProcessBuilder(command)
                .redirectOutput(fileOut)
                .redirectError(fileErr)
                .start()
        } else {
            serverThread = thread { kernelServer(kernelConfig, replConfig, defaultRuntimeProperties) }
        }
        beforeEach()
    }

    @AfterEach
    fun teardownServer() {
        afterEach()
        if (runInSeparateProcess) {
            serverProcess?.run {
                destroy()
                waitFor()
            }
            testLogger?.apply {
                fileOut?.let {
                    debug("Kernel output:")
                    it.forEachLine { line -> debug(line) }
                    it.delete()
                }
                fileErr?.let {
                    debug("Kernel errors:")
                    it.forEachLine { line -> debug(line) }
                    it.delete()
                }
            }
        } else {
            serverThread?.interrupt()
        }
    }

    fun createClientSocket(socketInfo: JupyterSocketInfo) = createSocket(socketInfo, context, hmac, kernelConfig, JupyterSocketSide.CLIENT)

    fun JupyterSocket.sendMessage(msgType: MessageType, content: MessageContent) {
        socket.sendMessage(Message(id = messageId, MessageData(header = makeHeader(msgType, sessionId = sessionId), content = content)), hmac)
    }

    fun JupyterSocket.receiveMessage() = socket.receiveRawMessage(socket.recv(), hmac).toMessage()

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

    companion object {
        private val rng = Random()
        private val usedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet()
        private const val portRangeStart = 32768
        private const val portRangeEnd = 65536
        private const val maxTrials = portRangeEnd - portRangeStart
        private val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        private val classpathArg = System.getProperty("java.class.path")

        private fun isPortAvailable(port: Int): Boolean {
            var tcpSocket: ServerSocket? = null
            var udpSocket: DatagramSocket? = null
            try {
                tcpSocket = ServerSocket(port)
                tcpSocket.reuseAddress = true
                udpSocket = DatagramSocket(port)
                udpSocket.reuseAddress = true
                return true
            } catch (_: IOException) {
            } finally {
                tcpSocket?.close()
                udpSocket?.close()
            }
            return false
        }

        fun randomPort() =
            generateSequence { portRangeStart + rng.nextInt(portRangeEnd - portRangeStart) }.take(maxTrials).find {
                isPortAvailable(it) && usedPorts.add(it)
            } ?: throw RuntimeException("No free port found")
    }
}
