
package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.*
import org.junit.*
import org.zeromq.ZMQ
import java.io.IOException
import java.net.ServerSocket
import java.util.*
import kotlin.concurrent.thread

class KernelServerTest {

    private val config = ConnectionConfig(
            ports = JupyterSockets.values().map { randomPort() }.toTypedArray(),
            transport = "tcp",
            signatureScheme = "hmac1-sha256",
            signatureKey = "")

    private val hmac = HMAC(config.signatureScheme, config.signatureKey)

    private var server: Thread? = null

    @Before
    fun setupServer() {
        server = thread { kernelServer(config) }
    }

    @After
    fun teardownServer() {
        Thread.sleep(100)
        server?.interrupt()
    }

    @Test
    fun testHeartbeat() {
        val context = ZMQ.context(1)
        with (context.socket(ZMQ.REQ)) {
            try {
                connect("${config.transport}://*:${config.ports[JupyterSockets.hb.ordinal]}")
                send("abc")
                val msg = recvStr()
                Assert.assertEquals("abc", msg)
            } finally {
                close()
                context.term()
            }
        }
    }

    @Test
    fun testStdin() {
        val context = ZMQ.context(1)
        with (context.socket(ZMQ.REQ)) {
            try {
                connect("${config.transport}://*:${config.ports[JupyterSockets.stdin.ordinal]}")
                sendMore("abc")
                sendMore("def")
                send("ok")
            } finally {
                close()
                context.term()
            }
        }
    }

    @Test
    fun testShell() {
        val context = ZMQ.context(1)
        with (context.socket(ZMQ.REQ)) {
            try {
                connect("${config.transport}://*:${config.ports[JupyterSockets.control.ordinal]}")
                sendMessage(makeNewMessage(makeHeader("kernel_info_request")), hmac)
                val msg = receiveMessage(recv(), hmac)
                Assert.assertEquals("kernel_info_reply", msg!!.header["msg_type"])
            } finally {
                close()
                context.term()
            }
        }
    }

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