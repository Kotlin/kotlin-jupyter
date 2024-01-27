package org.jetbrains.kotlinx.jupyter.test.repl

import jupyter.kotlin.receivers.ConstReceiver
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ReplWithReceiverTests : AbstractReplTest() {
    @Test
    fun testReplWithReceiver() {
        val value = 5
        val cp = classpath + File(ConstReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val repl = createRepl(resolutionInfoProvider, cp, null, scriptReceivers = listOf(ConstReceiver(value)))
        val res = repl.evalEx(EvalRequestData("value"))
        assertEquals(value, res.rawValue)
    }
}
