package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.*
import org.junit.Assert
import org.junit.Test
import org.zeromq.ZMQ

class ExecuteTests : KernelServerTestsBase() {

    fun doExecute(code : String) : Any? {
        val context = ZMQ.context(1)
        var shell = context.socket(ZMQ.REQ)
        var ioPub = context.socket(ZMQ.SUB)
        ioPub.subscribe(byteArrayOf())
        try {
            shell.connect("${config.transport}://*:${config.ports[JupyterSockets.shell.ordinal]}")
            ioPub.connect("${config.transport}://*:${config.ports[JupyterSockets.iopub.ordinal]}")
            shell.sendMessage("execute_request", content = jsonObject("code" to code))
            var msg = shell.receiveMessage()
            Assert.assertEquals("execute_reply", msg.header!!["msg_type"])
            msg = ioPub.receiveMessage()
            Assert.assertEquals("status", msg.header!!["msg_type"])
            Assert.assertEquals("busy", msg.content["execution_state"])
            msg = ioPub.receiveMessage()
            Assert.assertEquals("execute_input", msg.header!!["msg_type"])
            msg = ioPub.receiveMessage()
            Assert.assertEquals("execute_result", msg.header!!["msg_type"])
            var response = msg.content["data"]
            msg = ioPub.receiveMessage()
            Assert.assertEquals("status", msg.header!!["msg_type"])
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
}