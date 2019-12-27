package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.*
import org.junit.After
import org.junit.Before
import org.zeromq.ZMQ
import java.io.IOException
import java.net.ServerSocket
import java.util.*
import kotlin.concurrent.thread

open class KernelServerTestsBase {

    protected val config = KernelConfig(
            ports = JupyterSockets.values().map { randomPort() }.toTypedArray(),
            transport = "tcp",
            signatureScheme = "hmac1-sha256",
            signatureKey = "",
            scriptClasspath = classpath,
            resolverConfig = null)

    protected val hmac = HMAC(config.signatureScheme, config.signatureKey)

    private var server: Thread? = null

    protected val messageId = listOf(byteArrayOf(1))

    @Before
    fun setupServer() {
        server = thread { kernelServer(config) }
    }

    @After
    fun teardownServer() {
        server?.interrupt()
    }

    fun ZMQ.Socket.sendMessage(msgType: String, content : JsonObject) {
        sendMessage(Message(id = messageId, header = makeHeader(msgType), content = content), hmac)
    }

    fun ZMQ.Socket.receiveMessage() = receiveMessage(recv(), hmac)!!

    companion object {
        private val rng = Random()
        private val usedPorts = mutableSetOf<Int>()
        private const val portRangeStart = 32768
        private const val portRangeEnd = 65536

        @Synchronized
        fun randomPort(): Int {
            val res = generateSequence { portRangeStart + rng.nextInt(portRangeEnd - portRangeStart) }.find {
                try {
                    ServerSocket(it).close()
                    !usedPorts.contains(it)
                } catch (e: IOException) {
                    false
                }
            }!!
            usedPorts.add(res)
            return res
        }
    }
}
