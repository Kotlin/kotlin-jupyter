package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.HMAC
import org.jetbrains.kotlin.jupyter.JupyterSockets
import org.jetbrains.kotlin.jupyter.KernelConfig
import org.jetbrains.kotlin.jupyter.Message
import org.jetbrains.kotlin.jupyter.iKotlinClass
import org.jetbrains.kotlin.jupyter.makeHeader
import org.jetbrains.kotlin.jupyter.receiveMessage
import org.jetbrains.kotlin.jupyter.sendMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.zeromq.ZMQ
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

open class KernelServerTestsBase {

    private val config = KernelConfig(
            ports = JupyterSockets.values().map { randomPort() },
            transport = "tcp",
            signatureScheme = "hmac1-sha256",
            signatureKey = "",
            scriptClasspath = classpath,
            resolverConfig = null)

    private val args = config.toArgs().argsList().toTypedArray()

    private val sessionId = UUID.randomUUID().toString()

    protected val hmac = HMAC(config.signatureScheme, config.signatureKey)

    private var server: Process? = null

    protected val messageId = listOf(byteArrayOf(1))

    private val testLogger = LoggerFactory.getLogger("testKernel")
    private val fileOut = createTempFile("tmp-kernel-out", ".txt")
    private val fileErr = createTempFile("tmp-kernel-out", ".txt")

    @BeforeEach
    fun setupServer() {
        val command = ArrayList<String>().apply {
            add(javaBin)
            add("-cp")
            add(classpathArg)
            add(iKotlinClass.name)
            addAll(args)
        }

        server = ProcessBuilder(command)
                .redirectOutput(fileOut)
                .redirectError(fileErr)
                .start()
    }

    @AfterEach
    fun teardownServer() {
        server?.run {
            destroy()
            waitFor()
        }
        with(testLogger){
            debug("Kernel output:")
            debug(fileOut.readText())
            fileOut.delete()

            debug("Kernel errors:")
            debug(fileErr.readText())
            fileErr.delete()
        }
    }

    inner class ClientSocket(context: ZMQ.Context, private val socket: JupyterSockets) : ZMQ.Socket(context, socket.zmqClientType) {
        fun connect() = connect("${config.transport}://*:${config.ports[socket.ordinal]}")
    }

    fun ZMQ.Socket.sendMessage(msgType: String, content: JsonObject) {
        sendMessage(Message(id = messageId, header = makeHeader(msgType, sessionId = sessionId), content = content), hmac)
    }

    fun ZMQ.Socket.receiveMessage() = receiveMessage(recv(), hmac)!!

    companion object {
        private val rng = Random()
        private val usedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet()
        private const val portRangeStart = 32768
        private const val portRangeEnd = 65536
        private const val maxTrials = portRangeEnd - portRangeStart
        private val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        private val classpathArg = System.getProperty("java.class.path")

        fun randomPort()
            = generateSequence { portRangeStart + rng.nextInt(portRangeEnd - portRangeStart) }.take(maxTrials).find {
                try {
                    ServerSocket(it).close()
                    usedPorts.add(it)
                } catch (e: IOException) {
                    false
                }
            } ?: throw RuntimeException("No free port found")
    }
}
