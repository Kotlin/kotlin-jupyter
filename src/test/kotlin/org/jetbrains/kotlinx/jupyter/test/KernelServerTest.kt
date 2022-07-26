
package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.api.libraries.type
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageData
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.makeHeader
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.receiveRawMessage
import org.jetbrains.kotlinx.jupyter.sendMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.zeromq.ZMQ

@Execution(ExecutionMode.SAME_THREAD)
class KernelServerTest : KernelServerTestsBase() {

    override val context: ZMQ.Context = ZMQ.context(1)

    private fun connectClientSocket(socketInfo: JupyterSocketInfo) = createClientSocket(socketInfo).apply { connect() }

    @Test
    fun testHeartbeat() {
        with(connectClientSocket(JupyterSocketInfo.HB).socket) {
            try {
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
        with(connectClientSocket(JupyterSocketInfo.STDIN).socket) {
            try {
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
        with(connectClientSocket(JupyterSocketInfo.CONTROL).socket) {
            try {
                sendMessage(
                    Message(
                        id = messageId,
                        MessageData(header = makeHeader(MessageType.INTERRUPT_REQUEST))
                    ),
                    hmac
                )
                val msg = receiveRawMessage(recv(), hmac)
                assertEquals(MessageType.INTERRUPT_REPLY.type, msg.type)
            } finally {
                close()
                context.term()
            }
        }
    }
}
