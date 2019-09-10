package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.*
import org.junit.After
import org.junit.Before
import org.zeromq.ZMQ
import kotlin.concurrent.thread

open class KernelServerTestsBase {

    protected val config = KernelConfig(
            ports = JupyterSockets.values().map { KernelServerTest.randomPort() }.toTypedArray(),
            transport = "tcp",
            signatureScheme = "hmac1-sha256",
            signatureKey = "",
            classpath = classpath)

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
}
