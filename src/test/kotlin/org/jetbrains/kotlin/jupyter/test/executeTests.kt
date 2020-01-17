package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.*
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.zeromq.ZMQ

fun Message.type(): String {
    return header!!["msg_type"] as String
}

class ExecuteTests : KernelServerTestsBase() {

    private fun doExecute(code : String, hasResult: Boolean = true, ioPubChecker : (ZMQ.Socket) -> Unit = {}) : Any? {
        val context = ZMQ.context(1)
        val shell = context.socket(ZMQ.REQ)
        val ioPub = context.socket(ZMQ.SUB)
        ioPub.subscribe(byteArrayOf())
        try {
            shell.connect("${config.transport}://*:${config.ports[JupyterSockets.shell.ordinal]}")
            ioPub.connect("${config.transport}://*:${config.ports[JupyterSockets.iopub.ordinal]}")
            shell.sendMessage("execute_request", content = jsonObject("code" to code))
            var msg = shell.receiveMessage()
            Assert.assertEquals("execute_reply", msg.type())
            msg = ioPub.receiveMessage()
            Assert.assertEquals("status", msg.type())
            Assert.assertEquals("busy", msg.content["execution_state"])
            msg = ioPub.receiveMessage()
            Assert.assertEquals("execute_input", msg.type())

            ioPubChecker(ioPub)

            var response: Any? = null
            if (hasResult) {
                msg = ioPub.receiveMessage()
                Assert.assertEquals("execute_result", msg.type())
                response = msg.content["data"]
            }

            msg = ioPub.receiveMessage()
            Assert.assertEquals("status", msg.type())
            Assert.assertEquals("idle", msg.content["execution_state"])
            return response
        } finally {
            shell.close()
            ioPub.close()
            context.term()
        }
    }

    @Test
    fun testExecute(){
        val res = doExecute("2+2") as JsonObject
        Assert.assertEquals("4", res["text/plain"])
    }

    @Test
    fun testOutput(){
        val code = """
            for (i in 1..5) {
                Thread.sleep(200)
                print(i)
            }
        """.trimIndent()

        fun checker(ioPub: ZMQ.Socket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                Assert.assertEquals("stream", msg.type())
                Assert.assertEquals(i.toString(), msg.content!!["text"])
            }
        }

        val res = doExecute(code, false, ::checker)
        Assert.assertNull(res)
    }

    @Test
    fun testOutputMagic(){
        val code = """
            %output --max-buffer=2 --max-time=10000
            for (i in 1..5) {
                print(i)
            }
        """.trimIndent()

        val expected = arrayOf("12","34","5")

        fun checker(ioPub: ZMQ.Socket) {
            for (el in expected) {
                val msg = ioPub.receiveMessage()
                Assert.assertEquals("stream", msg.type())
                Assert.assertEquals(el, msg.content!!["text"])
            }
        }

        val res = doExecute(code, false, ::checker)
        Assert.assertNull(res)
    }

    @Test
    @Ignore
    fun testOutputStrings() {
        val code = """
            for (i in 1..5) {
                Thread.sleep(200)
                println("text" + i)
            }
        """.trimIndent()

        fun checker(ioPub: ZMQ.Socket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                Assert.assertEquals("stream", msg.type())
                Assert.assertEquals("text$i\n", msg.content!!["text"])
            }
        }

        val res = doExecute(code, false, ::checker)
        Assert.assertNull(res)
    }
}