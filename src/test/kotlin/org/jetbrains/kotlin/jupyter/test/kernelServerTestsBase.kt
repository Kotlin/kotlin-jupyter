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

    protected var server: Thread? = null

    protected val messageId = listOf(byteArrayOf(1))

    @Before
    fun setupServer() {
        server = thread { kernelServer(config) }
    }

    @After
    fun teardownServer() {
        Thread.sleep(100)
        server?.interrupt()
    }

    fun ZMQ.Socket.sendMessage(msgType: String, content : JsonObject): Unit {
        sendMessage(Message(id = messageId, header = makeHeader(msgType), content = content), hmac)
    }

    fun ZMQ.Socket.receiveMessage() = receiveMessage(recv(), hmac)!!

    companion object {
        private val rng = Random()
        private val portRangeStart = 32768
        private val portRangeEnd = 65536

        fun randomPort(): Int =
                generateSequence { portRangeStart + rng.nextInt(portRangeEnd - portRangeStart) }.find {
                    try {
                        ServerSocket(it).close()
                        true
                    }
                    catch (e: IOException) {
                        false
                    }
                }!!
    }
}
