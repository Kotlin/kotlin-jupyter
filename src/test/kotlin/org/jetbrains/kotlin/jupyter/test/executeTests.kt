package org.jetbrains.kotlin.jupyter.test

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.jupyter.ExecuteRequest
import org.jetbrains.kotlin.jupyter.ExecutionResult
import org.jetbrains.kotlin.jupyter.InputReply
import org.jetbrains.kotlin.jupyter.JupyterSockets
import org.jetbrains.kotlin.jupyter.KernelStatus
import org.jetbrains.kotlin.jupyter.MessageType
import org.jetbrains.kotlin.jupyter.StatusReply
import org.jetbrains.kotlin.jupyter.StreamResponse
import org.jetbrains.kotlin.jupyter.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.zeromq.ZMQ
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

fun JsonObject.string(key: String): String {
    return (get(key) as JsonPrimitive).content
}

@Timeout(100, unit = TimeUnit.SECONDS)
class ExecuteTests : KernelServerTestsBase() {

    private fun doExecute(
        code: String,
        hasResult: Boolean = true,
        ioPubChecker: (ZMQ.Socket) -> Unit = {},
        inputs: List<String> = emptyList(),
        allowStdin: Boolean = true,
    ): Any? {
        val context = ZMQ.context(1)
        val shell = ClientSocket(context, JupyterSockets.shell)
        val ioPub = ClientSocket(context, JupyterSockets.iopub)
        val stdin = ClientSocket(context, JupyterSockets.stdin)
        ioPub.subscribe(byteArrayOf())
        try {
            shell.connect()
            ioPub.connect()
            stdin.connect()

            shell.sendMessage(MessageType.EXECUTE_REQUEST, content = ExecuteRequest(code))
            inputs.forEach {
                stdin.sendMessage(MessageType.INPUT_REPLY, InputReply(it))
            }

            var msg = shell.receiveMessage()
            assertEquals(MessageType.EXECUTE_REPLY, msg.type)
            msg = ioPub.receiveMessage()
            assertEquals(MessageType.STATUS, msg.type)
            assertEquals(KernelStatus.BUSY, (msg.content as StatusReply).status)
            msg = ioPub.receiveMessage()
            assertEquals(MessageType.EXECUTE_INPUT, msg.type)

            ioPubChecker(ioPub)

            var response: Any? = null
            if (hasResult) {
                msg = ioPub.receiveMessage()
                val content = msg.content as ExecutionResult
                assertEquals(MessageType.EXECUTE_RESULT, msg.type)
                response = content.data
            }

            msg = ioPub.receiveMessage()
            assertEquals(MessageType.STATUS, msg.type)
            assertEquals(KernelStatus.IDLE, (msg.content as StatusReply).status)
            return response
        } finally {
            shell.close()
            ioPub.close()
            stdin.close()
            context.term()
        }
    }

    private fun testWithNoStdin(code: String) {
        doExecute(
            code,
            hasResult = false,
            allowStdin = false,
            ioPubChecker = {
                val msg = it.receiveMessage()
                assertEquals("stream", msg.type())
                assertTrue((msg.content!!["text"] as String).startsWith("java.io.IOException: Input from stdin is unsupported by the client"))
            }
        )
    }

    @Test
    fun testExecute() {
        val res = doExecute("2+2") as JsonObject
        assertEquals("4", res.string("text/plain"))
    }

    @Test
    fun testOutput() {
        val code =
            """
            for (i in 1..5) {
                Thread.sleep(200)
                print(i)
            }
            """.trimIndent()

        fun checker(ioPub: ZMQ.Socket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                assertEquals(MessageType.STREAM, msg.type)
                assertEquals(i.toString(), (msg.content as StreamResponse).text)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testOutputMagic() {
        val code =
            """
            %output --max-buffer=2 --max-time=10000
            for (i in 1..5) {
                print(i)
            }
            """.trimIndent()

        val expected = arrayOf("12", "34", "5")

        fun checker(ioPub: ZMQ.Socket) {
            for (el in expected) {
                val msg = ioPub.receiveMessage()
                val content = msg.content
                assertEquals(MessageType.STREAM, msg.type)
                assertTrue(content is StreamResponse)
                assertEquals(el, content.text)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testOutputStrings() {
        val code =
            """
            for (i in 1..5) {
                Thread.sleep(200)
                println("text" + i)
            }
            """.trimIndent()

        fun checker(ioPub: ZMQ.Socket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                assertEquals(MessageType.STREAM, msg.type)
                assertEquals("text$i" + System.lineSeparator(), (msg.content as StreamResponse).text)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testReadLine() {
        val code =
            """
            val answer = readLine()
            answer
            """.trimIndent()
        val res = doExecute(code, inputs = listOf("42"))
        assertEquals(jsonObject("text/plain" to "42"), res)
    }

    @Test
    fun testReadLineWithNoStdin() {
        testWithNoStdin("readLine() ?: \"blah\"")
    }

    @Test
    fun testStdinReadWithNoStdin() {
        testWithNoStdin("System.`in`.read()")
    }
}
