
package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.*
import org.junit.*
import org.zeromq.ZMQ

class KernelServerTest : KernelServerTestsBase() {

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
                sendMessage(Message(id = messageId, header = makeHeader("kernel_info_request")), hmac)
                val msg = receiveMessage(recv(), hmac)
                Assert.assertEquals("kernel_info_reply", msg!!.header!!["msg_type"])
            } finally {
                close()
                context.term()
            }
        }
    }
}