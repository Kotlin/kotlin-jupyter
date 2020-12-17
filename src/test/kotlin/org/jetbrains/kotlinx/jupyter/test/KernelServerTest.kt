
package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.JupyterSockets
import org.jetbrains.kotlinx.jupyter.Message
import org.jetbrains.kotlinx.jupyter.MessageData
import org.jetbrains.kotlinx.jupyter.MessageType
import org.jetbrains.kotlinx.jupyter.makeHeader
import org.jetbrains.kotlinx.jupyter.receiveMessage
import org.jetbrains.kotlinx.jupyter.sendMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.zeromq.ZMQ

class KernelServerTest : KernelServerTestsBase() {

    @Test
    fun testHeartbeat() {
        val context = ZMQ.context(1)
        with(ClientSocket(context, JupyterSockets.HB)) {
            try {
                connect()
                send("abc")
                val msg = recvStr()
                assertEquals("abc", msg)
            } finally {
                close()
                context.term()
            }
        }
    }

    @Test
    fun testStdin() {
        val context = ZMQ.context(1)
        with(ClientSocket(context, JupyterSockets.STDIN)) {
            try {
                connect()
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
        with(ClientSocket(context, JupyterSockets.CONTROL)) {
            try {
                connect()
                sendMessage(
                    Message(
                        id = messageId,
                        MessageData(header = makeHeader(MessageType.INTERRUPT_REQUEST))
                    ),
                    hmac
                )
                val msg = receiveMessage(recv(), hmac)
                assertEquals(MessageType.INTERRUPT_REPLY, msg.type)
            } finally {
                close()
                context.term()
            }
        }
    }
}
