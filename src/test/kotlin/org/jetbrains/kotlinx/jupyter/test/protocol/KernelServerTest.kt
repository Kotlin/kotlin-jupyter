
package org.jetbrains.kotlinx.jupyter.test.protocol

import org.jetbrains.kotlinx.jupyter.api.libraries.type
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterZmqSocketInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.zeromq.ZMQ

@Execution(ExecutionMode.SAME_THREAD)
class KernelServerTest : KernelServerTestsBase(runServerInSeparateProcess = true) {
    override val context: ZMQ.Context = ZMQ.context(1)

    private fun connectClientSocket(socketInfo: JupyterZmqSocketInfo) = createClientSocket(socketInfo).apply { connect() }

    @Test
    fun testHeartbeat() {
        with(connectClientSocket(JupyterZmqSocketInfo.HB)) {
            try {
                send("abc")
                val msg = recvString()
                assertEquals("abc", msg)
            } finally {
                close()
                context.term()
            }
        }
    }

    @Test
    fun testShell() {
        with(connectClientSocket(JupyterZmqSocketInfo.CONTROL)) {
            try {
                sendMessage(MessageType.INTERRUPT_REQUEST, null)
                val msg = receiveRawMessage()
                assertEquals(MessageType.INTERRUPT_REPLY.type, msg?.type)
            } finally {
                close()
                context.term()
            }
        }
    }
}
