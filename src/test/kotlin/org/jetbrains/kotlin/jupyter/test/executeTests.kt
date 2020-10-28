package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.JupyterSockets
import org.jetbrains.kotlin.jupyter.Message
import org.jetbrains.kotlin.jupyter.get
import org.jetbrains.kotlin.jupyter.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.zeromq.ZMQ
import java.util.concurrent.TimeUnit

fun Message.type(): String {
    return header!!["msg_type"] as String
}

@Timeout(100, unit = TimeUnit.SECONDS)
class ExecuteTests : KernelServerTestsBase() {

    private fun doExecute(
        code: String,
        hasResult: Boolean = true,
        ioPubChecker: (ZMQ.Socket) -> Unit = {},
        inputs: List<String> = emptyList(),
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

            shell.sendMessage("execute_request", content = jsonObject("code" to code))
            inputs.forEach {
                stdin.sendMessage("input_reply", jsonObject("value" to it))
            }

            var msg = shell.receiveMessage()
            assertEquals("execute_reply", msg.type())
            msg = ioPub.receiveMessage()
            assertEquals("status", msg.type())
            assertEquals("busy", msg.content["execution_state"])
            msg = ioPub.receiveMessage()
            assertEquals("execute_input", msg.type())

            ioPubChecker(ioPub)

            var response: Any? = null
            if (hasResult) {
                msg = ioPub.receiveMessage()
                assertEquals("execute_result", msg.type())
                response = msg.content["data"]
            }

            msg = ioPub.receiveMessage()
            assertEquals("status", msg.type())
            assertEquals("idle", msg.content["execution_state"])
            return response
        } finally {
            shell.close()
            ioPub.close()
            stdin.close()
            context.term()
        }
    }

    @Test
    fun testExecute() {
        val res = doExecute("2+2") as JsonObject
        assertEquals("4", res["text/plain"])
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
                assertEquals("stream", msg.type())
                assertEquals(i.toString(), msg.content!!["text"])
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
                assertEquals("stream", msg.type())
                assertEquals(el, msg.content!!["text"])
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
                assertEquals("stream", msg.type())
                assertEquals("text$i" + System.lineSeparator(), msg.content!!["text"])
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
}
